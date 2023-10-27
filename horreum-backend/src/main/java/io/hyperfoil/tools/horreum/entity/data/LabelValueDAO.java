package io.hyperfoil.tools.horreum.entity.data;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Type;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "label_values")
public class LabelValueDAO extends PanacheEntityBase implements Serializable {
    @Id
    @NotNull
    @Column(name = "dataset_id")
    public int datasetId;

    @Id
    @NotNull
    @Column(name = "label_id")
    public int labelId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelValueDAO value1 = (LabelValueDAO) o;
        return datasetId == value1.datasetId && labelId == value1.labelId && Objects.equals(value, value1.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, labelId, value);
    }

    @Override
    public String toString() {
        return "Value{" +
                "datasetId=" + datasetId +
                ", labelId=" + labelId +
                ", value=" + value +
                '}';
    }
}
