package io.hyperfoil.tools.horreum.entity.data;

import static java.lang.Integer.compare;

import java.util.Collection;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

@Entity(name = "Transformer")
@JsonIgnoreType
public class TransformerDAO extends OwnedEntityBase implements Comparable<TransformerDAO> {
   @Id
   @GenericGenerator(
         name = "transformerIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
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

   @ManyToOne(optional = false)
   @JoinColumn(name = "schema_id")
   public SchemaDAO schema;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "transformer_extractors")
   public Collection<ExtractorDAO> extractors;

   public String function;

   public int getSchemaId() {
      return schema.id;
   }

   public String getSchemaUri() {
      return schema.uri;
   }

   public String getSchemaName() {
      return schema.name;
   }

   public void setSchemaId(int schemaId) {
      schema = SchemaDAO.getEntityManager().getReference(SchemaDAO.class, schemaId);
   }

   // This gets invoked during deserialization on message bus
   public void ignoreSchemaUri(String uri) {
   }

   // This gets invoked during deserialization on message bus
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
