package io.hyperfoil.tools.horreum.entity.data;

import static java.lang.Integer.compare;

import java.util.Collection;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity(name = "Transformer")
public class TransformerDAO extends OwnedEntityBase implements Comparable<TransformerDAO> {
   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "transformerIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transformerIdGenerator")
   public Integer id;

   @NotNull
   public String name;

   public String description;

   @Column(name = "targetschemauri")
   public String targetSchemaUri;

   @JsonIgnore
   @ManyToOne(optional = false)
   @JoinColumn(name = "schema_id")
   public SchemaDAO schema;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "transformer_extractors")
   public Collection<Extractor> extractors;

   public String function;

   @JsonProperty(value = "schemaId", required = true)
   public int getSchemaId() {
      return schema.id;
   }

   @JsonProperty(value = "schemaUri", required = true)
   public String getSchemaUri() {
      return schema.uri;
   }

   @JsonProperty(value = "schemaName", required = true)
   public String getSchemaName() {
      return schema.name;
   }

   @JsonProperty(value = "schemaId")
   public void setSchemaId(int schemaId) {
      schema = SchemaDAO.getEntityManager().getReference(SchemaDAO.class, schemaId);
   }

   // This gets invoked during deserialization on message bus
   @JsonProperty(value = "schemaUri")
   public void ignoreSchemaUri(String uri) {
   }

   // This gets invoked during deserialization on message bus
   @JsonProperty(value = "schemaName")
   public void ignoreSchemaName(String name) {
   }

   @Override
   public int compareTo(TransformerDAO o) {
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
