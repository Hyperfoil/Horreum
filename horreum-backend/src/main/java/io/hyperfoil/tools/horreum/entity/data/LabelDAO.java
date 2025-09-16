package io.hyperfoil.tools.horreum.entity.data;

import java.util.Collection;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/* When we make changes to label we need to ensure that we remove label_values where label_id = id
*  After delete on extractors we need to execute:
*  https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/db/changeLog.xml#L2566
* */
@Entity(name = "label")
public class LabelDAO extends OwnedEntityBase {
    @Id
    @SequenceGenerator(name = "label_id_seq", sequenceName = "label_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "label_id_seq")
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
