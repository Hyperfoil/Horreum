package io.hyperfoil.tools.horreum.entity.data;

import java.util.Collection;

import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;
import static org.hibernate.id.enhanced.SequenceStyleGenerator.SEQUENCE_PARAM;

/* When we make changes to label we need to ensure that we remove label_values where label_id = id
*  After delete on extractors we need to execute:
*  https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/db/changeLog.xml#L2566
* */
@Entity(name="label")
public class LabelDAO extends OwnedEntityBase {
   @Id
   @GenericGenerator(
         name = "labelIdGenerator",
         type = SeqIdGenerator.class,
         parameters = { @Parameter(name = SEQUENCE_PARAM, value = "label_id_seq"), @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "labelIdGenerator")
   public Integer id;

   @NotNull
   public String name;

   @ManyToOne(optional = false)
   @JoinColumn(name = "schema_id")
   public SchemaDAO schema;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "label_extractors")
   public Collection<ExtractorDAO> extractors;

   public String function;

   @NotNull
   public boolean filtering = true;

   @NotNull
   public boolean metrics = true;

   public int getSchemaId() {
      return schema.id;
   }

   public void setSchema(int schemaId) {
      schema = getEntityManager().getReference(SchemaDAO.class, schemaId);
   }

   @Override
   public String toString() {
      return "LabelDAO{" +
              "id=" + id +
              ", name='" + name + '\'' +
              ", schemaId=" + schema.id +
              ", extractors=" + extractors +
              ", function='" + function + '\'' +
              ", filtering=" + filtering +
              ", metrics=" + metrics +
              '}';
   }

}
