package io.hyperfoil.tools.horreum.entity.data;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.SequenceGenerator;

import org.hibernate.annotations.Type;
import org.hibernate.query.NativeQuery;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.smallrye.common.constraint.NotNull;

/**
 * Purpose of this object is to represent derived run data.
 */
@Entity(name = "dataset")
@JsonIgnoreType
public class DatasetDAO extends OwnedEntityBase {

    @Id
    @SequenceGenerator(name = "datasetSequence", sequenceName = "dataset_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "datasetSequence")
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
    @Basic(fetch = FetchType.LAZY)
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode data;

    @ManyToOne(cascade = CascadeType.DETACH, fetch = FetchType.LAZY)
    @JoinColumn(name = "runid")
    public RunDAO run;

    @NotNull
    public int ordinal;

    @CollectionTable
    @ElementCollection
    public Collection<ValidationErrorDAO> validationErrors;

    public int getRunId() {
        return run.id;
    }

    public void setRunId(int runId) {
        run = getEntityManager().getReference(RunDAO.class, runId);
    }

    public String getFingerprint() {
        @SuppressWarnings("unchecked")
        List<JsonNode> fingerprintList = getEntityManager()
                .createNativeQuery("SELECT fingerprint FROM fingerprint WHERE dataset_id = ?")
                .setParameter(1, id).unwrap(NativeQuery.class)
                .addScalar("fingerprint", JsonBinaryType.INSTANCE)
                .getResultList();
        if (fingerprintList.size() > 0) {
            return fingerprintList.stream().findFirst().get().toString();
        } else {
            return "";
        }
    }

    public DatasetDAO.Info getInfo() {
        return new DatasetDAO.Info(id, run.id, ordinal, testid);
    }

    public static class EventNew {
        public DatasetDAO dataset;
        public boolean isRecalculation;

        public EventNew() {
        }

        public EventNew(DatasetDAO dataset, boolean isRecalculation) {
            this.dataset = dataset;
            this.isRecalculation = isRecalculation;
        }

        @Override
        public String toString() {
            return "Dataset.EventNew{" +
                    "dataset=" + dataset.id + " (" + dataset.run.id + "/" + dataset.ordinal +
                    "), isRecalculation=" + isRecalculation +
                    '}';
        }
    }

    public DatasetDAO() {
    }

    public DatasetDAO(RunDAO run, int ordinal, String description, JsonNode data) {
        this.run = run;
        this.start = run.start;
        this.stop = run.stop;
        this.testid = run.testid;
        this.owner = run.owner;
        this.access = run.access;

        this.ordinal = ordinal;
        this.description = description;
        this.data = data;
    }

    public static class Info {
        public int id;
        public int runId;
        public int ordinal;
        public int testId;

        public Info() {
        }

        public Info(int id, int runId, int ordinal, int testId) {
            this.id = id;
            this.runId = runId;
            this.ordinal = ordinal;
            this.testId = testId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Info info = (Info) o;
            return id == info.id && runId == info.runId && ordinal == info.ordinal && testId == info.testId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, runId, ordinal, testId);
        }

        @Override
        public String toString() {
            return "DatasetInfo{" +
                    "id=" + id +
                    ", runId=" + runId +
                    ", ordinal=" + ordinal +
                    ", testId=" + testId +
                    '}';
        }
    }

}
