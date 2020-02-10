package io.hyperfoil.tools.repo.entity.json;

import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Defines a mapping between {@link ViewComponent} through {@link ViewComponent#accessor} matching {@link #accessor}
 * and the {@link Schema}. Access is limited by access to the schema.
 */
@Entity
public class SchemaExtractor extends PanacheEntityBase {
   @Id
   @GeneratedValue
   private Long id;

   @NotNull
   @ManyToOne(fetch = FetchType.LAZY)
   @JsonbTypeSerializer(SchemaToUri.class)
   public Schema schema;

   @NotNull
   public String accessor;

   @NotNull
   public String jsonpath;

   public static class SchemaToUri implements JsonbSerializer<Schema> {
      @Override
      public void serialize(Schema obj, JsonGenerator generator, SerializationContext ctx) {
         generator.write(obj.uri);
      }
   }
}
