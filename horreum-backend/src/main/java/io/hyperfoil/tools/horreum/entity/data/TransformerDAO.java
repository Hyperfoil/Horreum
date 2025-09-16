package io.hyperfoil.tools.horreum.entity.data;

import static java.lang.Integer.compare;

import java.util.Collection;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

@Entity(name = "Transformer")
@JsonIgnoreType
public class TransformerDAO extends OwnedEntityBase implements Comparable<TransformerDAO> {
    @Id
    @SequenceGenerator(name = "transformeridgenerator", sequenceName = "transformeridgenerator", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transformeridgenerator")
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
