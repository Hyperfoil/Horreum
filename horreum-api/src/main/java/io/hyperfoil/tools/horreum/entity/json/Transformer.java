package io.hyperfoil.tools.horreum.entity.json;

import static java.lang.Integer.compare;

import java.util.Collection;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
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

@Entity
public class Transformer extends OwnedEntityBase implements Comparable<Transformer> {
   @Id
   @GeneratedValue
   public Integer id;

   @NotNull
   public String name;

   public String description;

   @Column(name = "targetschemauri")
   public String targetSchemaUri;

   @JsonIgnore
   @ManyToOne(optional = false)
   @JoinColumn(name = "schema_id")
   public Schema schema;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "transformer_extractors")
   public Collection<NamedJsonPath> extractors;

   public String function;

   @JsonProperty("schemaId")
   public int getSchemaId() {
      return schema.id;
   }

   @JsonProperty("schemaUri")
   public String getSchemaUri() {
      return schema.uri;
   }

   @JsonProperty("schemaName")
   public String getSchemaName() {
      return schema.name;
   }

   @Override
   public int compareTo(Transformer o) {
      if (o != null) {
         if (o == this) {
            return 0;
         } else {
            return compare(this.id, o.id);
         }
      } else {
         throw new IllegalArgumentException("cannot compare a null reference");
      }
   }
}
