package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * {@link java.time.Instant#now()} cannot be mocked in JDK17 so we need to use a mockable service.
 */
@ApplicationScoped
public class TimeService {
   public Instant now() {
      return Instant.now();
   }
}
