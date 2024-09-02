package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * For each {@link VariableDAO} a datapoint will be created for each run.
 */
@Entity(name = "DataPoint")
@Table(name = "DataPoint")
public class DataPointDAO extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "dataPointIdGenerator", sequenceName = "datapoint_seq")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dataPointIdGenerator")
    @Column(columnDefinition = "SERIAL")
    public Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    public DatasetDAO dataset;

    @NotNull
    @Column(columnDefinition = "timestamp")
    public Instant timestamp;

    @NotNull
    public double value;

    // for easier use in lambdas
    public double value() {
        return value;
    }

    @Override
    public String toString() {
        return id + "|" + dataset.id + "@" + timestamp + ": " + value;
    }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    public VariableDAO variable;

    public void setDatasetId(int datasetId) {
        dataset = DatasetDAO.getEntityManager().getReference(DatasetDAO.class, datasetId);
    }

    public int getDatasetId() {
        return dataset.id;
    }

}
