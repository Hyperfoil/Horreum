package io.hyperfoil.tools.horreum.entity.json;

import java.util.Objects;

import javax.persistence.Embeddable;

import org.hibernate.annotations.Type;

import io.hyperfoil.tools.yaup.json.Json;

// If the test has no run with these tags uploaded for more than this duration (in ms)
// we send a notification about missing regular run upload. If the value is non-positive
// no notifications are emitted.
@Embeddable
public class StalenessSettings {
   // when this is null it matches any tags
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public Json tags;

   public long maxStaleness;

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StalenessSettings that = (StalenessSettings) o;
      return maxStaleness == that.maxStaleness &&
            Objects.equals(tags, that.tags);
   }

   @Override
   public int hashCode() {
      return Objects.hash(tags, maxStaleness);
   }
}
