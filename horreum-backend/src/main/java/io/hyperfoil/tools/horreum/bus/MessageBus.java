package io.hyperfoil.tools.horreum.bus;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.mapper.RunMapper;
import io.hyperfoil.tools.horreum.mapper.TestMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.ErrorReporter;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.hyperfoil.tools.horreum.svc.Util;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.MessageConsumer;

// Wraps Vert.x EventBus with transactions and persistence.
@Startup
@ApplicationScoped
public class MessageBus {
   private static final Logger log = Logger.getLogger(MessageBus.class);
   private static final String ERROR_SUBJECT = " Error in MessageBus handler";

   @ConfigProperty(name = "horreum.messagebox.retry.after", defaultValue = "5m")
   Duration retryAfter;

   @Inject
   TransactionManager tm;

   @Inject
   EventBus eventBus;

   @Inject
   EntityManager em;

   @Inject
   RoleManager roleManager;

   @Inject
   Vertx vertx;

   @Inject
   ErrorReporter errorReporter;

   private final ConcurrentMap<String, Integer> flags = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Class<?>> payloadClasses = new ConcurrentHashMap<>();
   private final ConcurrentMap<Integer, TaskQueue> taskQueues = new ConcurrentHashMap<>();

   private final List<Runnable> unregisters = new ArrayList<>();

   @PostConstruct
   void init() {
      eventBus.registerDefaultCodec(Message.class, new MessageBusCodec());
   }

   @PreDestroy
   void destroy() {
      // required for dev-mode to work correctly
      eventBus.unregisterDefaultCodec(Message.class);
      unregisters.forEach(Runnable::run);
   }

   @Transactional(Transactional.TxType.MANDATORY)
   public void publish(String channel, int testId, Object payload) {
      JsonNode json = Util.OBJECT_MAPPER.valueToTree(payload);
      Integer componentFlag = flags.get(channel);
      BigInteger id;
      if (componentFlag != null && componentFlag != 0) {
         try (CloseMe ignored = roleManager.withRoles(Collections.singleton(Roles.HORREUM_MESSAGEBUS))) {
            id = (BigInteger) em.createNativeQuery("INSERT INTO messagebus (id, \"timestamp\", channel, testid, message, flags) VALUES (nextval('messagebus_seq'), NOW(), ?1, ?2, ?3, ?4) RETURNING id", BigInteger.class)
                  .unwrap(NativeQuery.class)
                  .setParameter(1, channel)
                  .setParameter(2, testId)
                  .setParameter(3, json, JsonBinaryType.INSTANCE)
                  .setParameter(4, componentFlag)
                  .getSingleResult();
         }
      } else {
         id = BigInteger.ZERO;
         componentFlag = 0;
      }
      try {
         int flag = componentFlag;
         log.debugf("Publishing %d on test %d with flag %X on %s: %s", id.longValue(), testId, flag, channel, payload);
         Util.doAfterCommitThrowing(tm, () -> {
            log.debugf("Sending %d on test %d with flag %X to eventbus %s ", id.longValue(), testId, flag, channel);
            eventBus.publish(channel, new Message(id.longValue(), testId, flag, payload));
         });
      } catch (RollbackException e) {
         log.debug("Not publishing the event as the transaction has been marked rollback-only");
      } catch (SystemException e) {
         log.errorf(e, "Failed to publish event %s: %s after transaction completion", channel, payload);
      }
   }

   public <T> AutoCloseable subscribe(String channel, String component, Class<T> payloadClass, Handler<T> handler) {
      payloadClasses.compute(channel, (c, current) -> {
         if (current == null || current.isAssignableFrom(payloadClass)) {
            return payloadClass;
         } else if (payloadClass.isAssignableFrom(current)) {
            return current;
         } else {
            throw new IllegalArgumentException("Incompatible expectations for the message payload: One subscriber expects " + payloadClass.getName() + ", another expects " + current.getName());
         }
      });
      int index = registerIndex(channel, component);
      log.debugf("Channel %s, component %s has index %d", channel, component, index);
      MessageConsumer<Object> consumer = eventBus.consumer(channel, event -> {
         if (!(event.body() instanceof Message)) {
            log.errorf("Not a message on %s: %s", channel, event.body());
            return;
         }
         Message msg = (Message) event.body();
         if ((msg.componentFlags & (1 << index)) == 0) {
            log.debugf("%s ignoring message %d on %s with flags %X: doesn't match index %d", component, msg.id, channel, msg.componentFlags, index);
            return;
         }
         executeForTest(msg.testId, () -> {
            try {
               //handler.handle(payloadClass.cast(msg.payload));
               handler.handle(payloadClass.cast(msg.payload));
               Util.withTx(tm, () -> {
                  try {
                     if (tm.getStatus() == Status.STATUS_ACTIVE) {
                        // It's not possible to do the DELETE in the same query (e.g. CTE support only one update per row)
                        // so we'll remove the record with a trigger
                        int updateCount = em.createNativeQuery("UPDATE messagebus SET flags = flags & ~(1 << ?1) WHERE id = ?2")
                              .setParameter(1, index).setParameter(2, msg.id).executeUpdate();
                        log.debugf("%s consumed %d on %s - %d records updated", component, msg.id, channel, updateCount);
                     } else {
                        log.debugf("Rolling back, %s cannot consume %d on %s", component, msg.id, channel);
                     }
                  } catch (SystemException e) {
                     log.error("Exception querying transaction status", e);
                     try {
                        tm.setRollbackOnly();
                     } catch (SystemException se2) {
                        log.error("Cannot mark TM as rollback-only", se2);
                     }
                  }
                  return null;
               });
            } catch (Throwable t) {
               errorReporter.reportException(t, ERROR_SUBJECT, "Exception in handler for message bus channel %s, message %s%n%n", channel, msg.payload);
            }
         });
      });
      unregisters.add(consumer::unregister);
      return () -> {
         removeIndex(channel, index);
         int newFlags = flags.compute(channel, (c, current) -> current != null ? current & ~(1 << index) : 0);
         consumer.unregister();
         log.debugf("Unregistered index %d on channel %s, new flags: %d", index, channel, Integer.valueOf(newFlags));
      };
   }

   private Object mapToDTO(Object entity) {
      if (entity instanceof RunDAO)
         return RunMapper.from((RunDAO) entity);
      else if (entity instanceof TestDAO)
         return TestMapper.from((TestDAO) entity);
      // lets just return for now
      return entity;
   }

   private int registerIndex(String channel, String component) {
      Integer index;
      do {
         index = tryRegisterIndex(channel, component);
         Thread.yield();
      } while (index == null);
      int finalIndex = index;
      flags.compute(channel, (c, current) -> (current == null ? 0 : current) | (1 << finalIndex));
      return index;
   }

   @Transactional
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   Integer tryRegisterIndex(String channel, String component) {
      List<?> list = em.createNativeQuery("SELECT index FROM messagebus_subscriptions WHERE channel = ?1 AND component = ?2")
            .setParameter(1, channel).setParameter(2, component).getResultList();
      if (list.isEmpty()) {
         log.debugf("Component %s is not registered on channel %s", component, channel);
         Integer index = (Integer) em.createNativeQuery("SELECT COALESCE(MAX(index) + 1, 0) FROM messagebus_subscriptions WHERE channel = ?1")
               .setParameter(1, channel).getSingleResult();
         if (em.createNativeQuery("INSERT INTO messagebus_subscriptions(channel, index, component) VALUES (?1, ?2, ?3) ON CONFLICT DO NOTHING")
               .setParameter(1, channel).setParameter(2, index).setParameter(3, component).executeUpdate() == 1) {
            return index;
         } else {
            log.debugf("Channel %s component %s trying to use different index", channel, component);
            return null;
         }
      } else {
         return (Integer) list.get(0);
      }
   }

   // This actually happens only in tests
   @Transactional
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   void removeIndex(String channel, int index) {
      if (em.createNativeQuery("DELETE FROM messagebus_subscriptions WHERE channel = ?1 AND index = ?2")
            .setParameter(1, channel).setParameter(2, index).executeUpdate() != 1) {
         throw new IllegalStateException();
      }
   }

   @Scheduled(
         every = "{horreum.messagebus.retry.check:5m}",
         delayed = "{horreum.messagebus.retry.delay:30s}",
         concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
   public void retryFailedMessages() {

      try(ScrollableResults<ChannelMessage> results = (ScrollableResults<ChannelMessage>) em.createNativeQuery("SELECT channel, id, testid, flags, message FROM messagebus WHERE \"timestamp\" + make_interval(secs => ?1) <= now()")
            .setParameter(1, retryAfter.toSeconds())
            .unwrap(NativeQuery.class)
            .addScalar("channel", StandardBasicTypes.TEXT)
            .addScalar("id", StandardBasicTypes.LONG)
            .addScalar("testid", StandardBasicTypes.INTEGER)
            .addScalar("flags", StandardBasicTypes.INTEGER)
            .addScalar("message", JsonBinaryType.INSTANCE)
              .setTupleTransformer((tuples, aliases) -> {
                 ChannelMessage sm = new ChannelMessage((String) tuples[0], (Long) tuples[1], (Integer) tuples[2],
                         (Integer) tuples[3], (JsonNode) tuples[4]);
                 return sm;
              })
              .setReadOnly(true)
              .setFetchSize(100)
              .scroll(ScrollMode.FORWARD_ONLY)) {
         while (results.next()) {
            ChannelMessage row = results.get();
            Class<?> type = payloadClasses.get(row.getChannel());
            // theoretically the type might not be set if the initial delay is too short
            // and components are not registered yet
            if (type != null) {
               log.debugf("Retrying message %d (#%d) in channel %s (%s)", row.getId(), results.getRowNumber(), row.getChannel(), type.getName());
               try {
                  Object payload = Util.OBJECT_MAPPER.treeToValue(row.getMessage(), type);
                  eventBus.publish(row.getChannel(), new Message(row.getId(), row.getTestId(), row.getFlags(), payload));
               } catch (JsonProcessingException e) {
                  String jsonStr = String.valueOf(row.message);
                  if (jsonStr.length() > 200) {
                     jsonStr = jsonStr.substring(0, 200) + "...";
                  }
                  errorReporter.reportException(e, ERROR_SUBJECT, "Exception loading message to retry in bus channel %s, message %s%n%n", row.getChannel(), jsonStr);
               }
            }
         }
      } catch (Throwable t) {
         log.error("Failed to retry publishing some messages", t);
      }
   }

   public void executeForTest(int testId, Runnable runnable) {
      Runnable task = Util.wrapForBlockingExecution(runnable);
      vertx.executeBlocking(promise -> {
         try {
            TaskQueue queue = taskQueues.computeIfAbsent(testId, TaskQueue::new);
            queue.executeOrAdd(task);
         } catch (Exception e) {
            log.error("Failed to execute blocking task", e);
         } finally {
            promise.complete();
         }
      });
   }

   static class Message {
      final long id;
      final int testId;
      final int componentFlags;
      final Object payload;

      public Message(long id, int testId, int componentFlags, Object payload) {
         this.id = id;
         this.testId = testId;
         this.componentFlags = componentFlags;
         this.payload = payload;
      }
   }

   static class MessageBusCodec implements MessageCodec<Message, Message> {
      @Override
      public void encodeToWire(Buffer buffer, Message message) {
         throw new IllegalStateException("Local only!");
      }

      @Override
      public Message decodeFromWire(int pos, Buffer buffer) {
         throw new IllegalStateException("Local only!");
      }

      @Override
      public Message transform(Message message) {
         return message;
      }

      @Override
      public String name() {
         return MessageBusCodec.class.getName();
      }

      @Override
      public byte systemCodecID() {
         return -1;
      }
   }

   class ChannelMessage {
      private final String channel;
      private final Long id;
      private final Integer testId;
      private final Integer flags;
      private final JsonNode message;

      public ChannelMessage(String channel, Long id, Integer testId, Integer flags, JsonNode message) {
         this.channel = channel;
         this.id = id;
         this.testId = testId;
         this.flags = flags;
         this.message = message;
      }

      public String getChannel() {
         return channel;
      }

      public Long getId() {
         return id;
      }

      public Integer getTestId() {
         return testId;
      }

      public Integer getFlags() {
         return flags;
      }

      public JsonNode getMessage() {
         return message;
      }
   }
}
