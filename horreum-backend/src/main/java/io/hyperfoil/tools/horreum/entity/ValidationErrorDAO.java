package io.hyperfoil.tools.horreum.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;

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

    @Override
    public String toString() {
        return "ValidationErrorDAO{" +
                "schema=" + schema.id +
                ", error=" + error +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ValidationErrorDAO that = (ValidationErrorDAO) o;
        return Objects.equals(schema.id, that.schema.id) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema.id, error);
    }
}
