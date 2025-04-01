package io.hyperfoil.tools.horreum.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.logging.Log;

public final class TestUtil {

    private TestUtil() {
    }

    public static void assertEmptyArray(JsonNode node) {
        assertNotNull(node);
        assertTrue(node.isArray());
        assertTrue(node.isEmpty());
    }

    @SuppressWarnings("BusyWait")
    public static void eventually(Runnable test) {
        long now = System.currentTimeMillis();
        do {
            try {
                test.run();
                return;
            } catch (AssertionError e) {
                Log.debug("Ignoring failed assertion", e);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                fail("Interrupted while polling condition.");
            }
        } while (System.currentTimeMillis() < now + TimeUnit.SECONDS.toMillis(30));
        test.run();
    }

    public static void eventually(BooleanSupplier test) {
        long now = System.currentTimeMillis();
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
        } while (System.currentTimeMillis() < now + TimeUnit.SECONDS.toMillis(30));
        fail("Failed waiting for test to become true");
    }

}
