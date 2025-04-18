package io.hyperfoil.tools.horreum.entity.data;

import java.time.Instant;
import java.util.Collection;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;

@Entity(name = "run")
@JsonIgnoreType
public class RunDAO extends OwnedEntityBase {

    @Id
    @SequenceGenerator(name = "runSequence", sequenceName = "run_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "runSequence")
    public Integer id;

    @NotNull
    @Column(name = "start", columnDefinition = "timestamp")
    public Instant start;

    @NotNull
    @Column(name = "stop", columnDefinition = "timestamp")
    public Instant stop;

    public String description;

    @NotNull
    public Integer testid;

    @NotNull
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode data;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode metadata;

    @NotNull
    @Column(columnDefinition = "boolean default false")
    public boolean trashed;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    public Collection<DatasetDAO> datasets;

    @CollectionTable
    @ElementCollection
    public Collection<ValidationErrorDAO> validationErrors;

    @Override
    public String toString() {
        return "RunDAO{" +
                "id=" + id +
                ", start=" + start +
                ", stop=" + stop +
                ", description='" + description + '\'' +
                ", testid=" + testid +
                ", data=" + data +
                ", metadata=" + metadata +
                ", trashed=" + trashed +
                ", datasets=" + datasets +
                ", validationErrors=" + validationErrors +
                '}';
    }
}
