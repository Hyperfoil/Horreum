package io.hyperfoil.tools.horreum.bus;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.horreum.svc.Util;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class MessageBusTest {
   protected static final Logger log = Logger.getLogger(MessageBusTest.class);
   protected static final String CHANNEL = "foobar";
   protected static final RuntimeException RETRY_EXCEPTION = new RuntimeException("Once more please", null, false, false) {};

   @Inject
   MessageBus messageBus;

   @Inject
   EntityManager em;

   @Inject
   TransactionManager tm;

   @Test
   public void testRetry() throws InterruptedException {
      AtomicInteger counter = new AtomicInteger();
      CountDownLatch firstLatch = new CountDownLatch(1);
      CountDownLatch secondLatch = new CountDownLatch(1);
      messageBus.subscribe(CHANNEL, "test", String.class, str -> {
         if (counter.getAndIncrement() == 0) {
            firstLatch.countDown();
            throw RETRY_EXCEPTION;
         }
         secondLatch.countDown();
      });
      Util.withTx(tm, () -> {
         messageBus.publish(CHANNEL, 1, "foo");
         return null;
      });
      assertTrue(firstLatch.await(10, TimeUnit.SECONDS));
      assertEquals(1, counter.get());

      setTimestampsInThePast();
      messageBus.retryFailedMessages();
      assertTrue(secondLatch.await(10, TimeUnit.SECONDS));
      assertEquals(2, counter.get());
      TestUtil.eventually(() -> TestUtil.isMessageBusEmpty(tm, em));
   }

   private void setTimestampsInThePast() {
      Util.withTx(tm, () -> {
         assertTrue(em.createNativeQuery("UPDATE messagebus SET timestamp = timestamp - make_interval(mins => 10)").executeUpdate() > 0);
         return null;
      });
   }

   @Test
   public void testRetryManyWithExceptions() throws InterruptedException, TimeoutException {
      AtomicInteger alive = new AtomicInteger(100);
      Phaser phaser = new Phaser(100);
      int currentPhase = phaser.getPhase();
      messageBus.subscribe(CHANNEL, "testMany", String.class, str -> {
         if (ThreadLocalRandom.current().nextBoolean()) {
            int value = alive.decrementAndGet();
            log.debugf("Decreased to %d", value);
            phaser.arriveAndDeregister();
         } else {
            phaser.arrive();
            throw RETRY_EXCEPTION;
         }
      });
      Util.withTx(tm, () -> {
         for (int i = 0; i < 100; ++i) {
            messageBus.publish(CHANNEL, 1, "foo" + i);
         }
         return null;
      });
      // We need to prevent concurrent execution of the same message - normally this is achieved
      // through time separation. Retries will run sequentially, too, from the main thread.
      currentPhase = phaser.awaitAdvanceInterruptibly(currentPhase, 10, TimeUnit.SECONDS);
      awaitMessageBus(alive.get());
      setTimestampsInThePast();
      long start = System.currentTimeMillis();
      for (;;) {
         messageBus.retryFailedMessages();
         currentPhase = phaser.awaitAdvanceInterruptibly(currentPhase, 10, TimeUnit.SECONDS);
         if (alive.get() < 0) {
            fail("Repeated too many times");
         } else if (alive.get() == 0) {
            break;
         }
         awaitMessageBus(alive.get());
         if (System.currentTimeMillis() > start + 10000) {
            fail("Didn't process all messages on time: " + alive.get());
         }
      }
      TestUtil.eventually(() -> TestUtil.isMessageBusEmpty(tm, em));
      log.debug("Test completed");
   }

   private void awaitMessageBus(int expectedMessages) {
      TestUtil.eventually(() -> expectedMessages == Util.withTx(tm, () -> {
         long count = ((BigInteger) em.createNativeQuery("SELECT COUNT(*) FROM messagebus").getSingleResult()).longValue();
         log.debugf("Message bus has %d messages, expected %d", count, expectedMessages);
         try {
            Thread.sleep(10);
         } catch (InterruptedException e) {
            throw new RuntimeException();
         }
         return count;
      }));
   }
}
