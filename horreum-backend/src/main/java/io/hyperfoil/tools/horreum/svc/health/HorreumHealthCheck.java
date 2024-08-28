package io.hyperfoil.tools.horreum.svc.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class HorreumHealthCheck implements HealthCheck {
   @Override
   public HealthCheckResponse call() {
      return HealthCheckResponse.up("Horreum is ready to accept requests");
   }
}
