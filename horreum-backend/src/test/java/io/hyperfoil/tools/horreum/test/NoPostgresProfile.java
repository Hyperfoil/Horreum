package io.hyperfoil.tools.horreum.test;

import java.util.Map;

public class NoPostgresProfile extends NoGrafanaProfile {
   @Override
   public Map<String, String> getConfigOverrides() {
      Map<String, String> map = super.getConfigOverrides();
      // TODO: This profile does not prevent Postgres from starting ATM
      // Hibernate will fail because it wants to start default persistence unit
      // and quarkus.hibernate-orm.enabled is a build-time option.
//      map.put("horreum.test.postgres.enabled", "false");
//      map.put("quarkus.datasource.jdbc.url", "");
      map.put("quarkus.hibernate-orm.database.generation", "none");
      map.put("quarkus.liquibase.migration.migrate-at-start", "false");
      map.put("quarkus.liquibase.migrate-at-start", "false");
      map.put("horreum.alerting.missing.dataset.check", "off");
      map.put("horreum.alerting.expected.run.check", "off");
      map.put("horreum.transformationlog.check", "off");
      return map;
   }
}
