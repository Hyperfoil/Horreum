package io.hyperfoil.tools.horreum.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Type;

@Embeddable
public class ValidationErrorDAO {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   public SchemaDAO schema;

   @NotNull
   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode error;

   public void setSchema(int id) {
      schema = SchemaDAO.getEntityManager().getReference(SchemaDAO.class, id);
   }

   public Integer getSchemaId() {
      return schema == null ? null : schema.id;
   }
}
