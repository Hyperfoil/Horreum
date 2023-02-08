package io.hyperfoil.tools.horreum.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.svc.Util;

public final class TestUtil {
   private static final Logger log = Logger.getLogger(TestUtil.class);

   private static final int TIMEOUT_DUR = 100;

   private TestUtil() {}

   public static void assertEmptyArray(JsonNode node) {
      assertNotNull(node);
      assertTrue(node.isArray());
      assertTrue(node.isEmpty());
   }

   @SuppressWarnings("BusyWait")
   public static void eventually(Runnable test) {
      long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_DUR);
      do {
         try {
            test.run();
            return;
         } catch (AssertionError e) {
            log.debug("Ignoring failed assertion", e);
         }
         try {
            Thread.sleep(10);
         } catch (InterruptedException e) {
            fail("Interrupted while polling condition.");
         }
      } while (System.currentTimeMillis() < timeout);
      test.run();
   }

   public static void eventually(BooleanSupplier test) {
      long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_DUR);
      do {
         if (test.getAsBoolean()) {
            return;
         }
         try {
            //noinspection BusyWait
            Thread.sleep(10);
         } catch (InterruptedException e) {
            fail("Interrupted while polling condition.");
         }
      } while (System.currentTimeMillis() < timeout);
      fail("Failed waiting for test to become true");
   }

   public static boolean isMessageBusEmpty(TransactionManager tm, EntityManager em) {
      return Util.withTx(tm, () -> em.createNativeQuery("SELECT id FROM messagebus").getResultList().isEmpty());
   }
}
