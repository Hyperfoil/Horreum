package io.hyperfoil.tools.horreum.entity.json;

import java.util.Collection;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class Transformer extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @NotNull
   public String name;

   public String description;

   @JsonIgnore
   @ManyToOne(optional = false)
   @JoinColumn(name = "schema_id")
   public Schema schema;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "transformer_extractors")
   public Collection<NamedJsonPath> extractors;

   public String function;

   @NotNull
   public String owner;

   @NotNull
   public Access access;

   @JsonProperty("schemaId")
   public int getSchemaId() {
      return schema.id;
   }

   @JsonProperty("schemaUri")
   public String getSchemaUri() {
      return schema.uri;
   }
}
