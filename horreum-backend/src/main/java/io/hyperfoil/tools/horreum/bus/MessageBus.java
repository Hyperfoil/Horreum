package io.hyperfoil.tools.horreum.bus;

import io.hyperfoil.tools.horreum.server.ErrorReporter;
import io.hyperfoil.tools.horreum.svc.Util;
import io.quarkus.runtime.Startup;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.MessageConsumer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Wraps Vert.x EventBus with transactions and persistence.
@Startup
@ApplicationScoped
public class MessageBus {
   private static final Logger log = Logger.getLogger(MessageBus.class);
   private static final String ERROR_SUBJECT = " Error in MessageBus handler";

   @Inject
   EventBus eventBus;

   @Inject
   Vertx vertx;

   @Inject
   ErrorReporter errorReporter;

   private final ConcurrentMap<String, Integer> flags = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Class<?>> payloadClasses = new ConcurrentHashMap<>();
   private final ConcurrentMap<Integer, TaskQueue> taskQueues = new ConcurrentHashMap<>();


   @PostConstruct
   void init() {
      eventBus.registerDefaultCodec(Message.class, new MessageBusCodec());
   }

   @PreDestroy
   void destroy() {
      // required for dev-mode to work correctly
      eventBus.unregisterDefaultCodec(Message.class);
   }

   @Transactional(Transactional.TxType.MANDATORY)
   public void publish(MessageBusChannels channel, int testId, Object payload) {
       log.debugf("Publishing test %d on %s: %s", testId,  channel, payload);
       eventBus.publish(channel.name(), new Message(BigInteger.ZERO.longValue(), testId, 0, payload));
   }

   public <T> AutoCloseable subscribe(MessageBusChannels channel, String component, Class<T> payloadClass, Handler<T> handler) {
      payloadClasses.compute(channel.name(), (c, current) -> {
         if (current == null || current.isAssignableFrom(payloadClass)) {
            return payloadClass;
         } else if (payloadClass.isAssignableFrom(current)) {
            return current;
         } else {
            throw new IllegalArgumentException("Incompatible expectations for the message payload: One subscriber expects " + payloadClass.getName() + ", another expects " + current.getName());
         }
      });
      eventBus.consumer(channel.name(), event -> {
         if (!(event.body() instanceof Message)) {
            log.errorf("Not a message on %s: %s", channel.name(), event.body());
            return;
         }
         Message msg = (Message) event.body();

         executeForTest(msg.testId, () -> {
            try {
               handler.handle(payloadClass.cast(msg.payload));
            } catch (Throwable t) {
               errorReporter.reportException(t, ERROR_SUBJECT, "Exception in handler for message bus channel %s, message %s%n%n", channel.name(), msg.payload);
            }
         });
      });
      return () -> {
         log.debugf("Unregistered on channel %s", channel.name());
      };
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

}
