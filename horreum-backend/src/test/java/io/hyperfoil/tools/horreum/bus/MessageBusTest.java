package io.hyperfoil.tools.horreum.bus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;

import org.junit.jupiter.api.Assertions;
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
   protected static final String CHANNEL = "foobar";
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
            throw new RuntimeException("Once more please");
         }
         secondLatch.countDown();
      });
      Util.withTx(tm, () -> {
         messageBus.publish(CHANNEL, "foo");
         return null;
      });
      assertTrue(firstLatch.await(10, TimeUnit.SECONDS));
      assertEquals(1, counter.get());

      Util.withTx(tm, () -> {
         assertTrue(em.createNativeQuery("UPDATE messagebus SET timestamp = timestamp - make_interval(mins => 10)").executeUpdate() > 0);
         return null;
      });
      messageBus.retryFailedMessages();
      assertTrue(secondLatch.await(10, TimeUnit.SECONDS));
      assertEquals(2, counter.get());
      TestUtil.eventually(() -> TestUtil.isMessageBusEmpty(tm, em));
   }
}
