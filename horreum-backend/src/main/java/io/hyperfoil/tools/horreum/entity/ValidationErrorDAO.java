package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;

@Embeddable
public class ValidationErrorDAO {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JsonIgnore
   public SchemaDAO schema;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode error;

   @JsonProperty(value = "schemaId", required = true)
   public void setSchema(int id) {
      schema = SchemaDAO.getEntityManager().getReference(SchemaDAO.class, id);
   }

   @JsonProperty(value = "schemaId", required = true)
   public Integer getSchemaId() {
      return schema == null ? null : schema.id;
   }
}
