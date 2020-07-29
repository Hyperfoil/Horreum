package io.hyperfoil.tools.horreum.entity.json;

import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

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
   @JsonbTypeSerializer(SchemaToUri.class)
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

   public static class SchemaToUri implements JsonbSerializer<Schema> {
      @Override
      public void serialize(Schema obj, JsonGenerator generator, SerializationContext ctx) {
         generator.write(obj.uri);
      }
   }
}
