package io.hyperfoil.tools.horreum.entity.json;

import java.io.IOException;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Defines a mapping between {@link ViewComponent} through {@link ViewComponent#accessors} matching {@link #accessor}
 * and the {@link Schema}. Access is limited by access to the schema.
 */
@Entity(name = "schemaextractor")
public class SchemaExtractor extends PanacheEntityBase {
   @Id
   @GeneratedValue
   private Integer id;

   @NotNull
   @ManyToOne(fetch = FetchType.LAZY)
   @JsonSerialize(using = SchemaToUri.class)
   public Schema schema;

   @NotNull
   public String accessor;

   @NotNull
   public String jsonpath;

   // TODO: eventually we could have syntax for min, max, sum...
   public static boolean isArray(String accessor) {
      return accessor.endsWith("[]");
   }

   public static String arrayName(String accessor) {
      return accessor.substring(0, accessor.length() - 2);
   }

   @JsonProperty("schemaId")
   private int schemaId() {
      return schema.id;
   }

   public static class SchemaToUri extends JsonSerializer<Schema> {
      @Override
      public void serialize(Schema schema, JsonGenerator gen, SerializerProvider serializers) throws IOException {
         gen.writeString(schema.uri);
      }
   }
}
