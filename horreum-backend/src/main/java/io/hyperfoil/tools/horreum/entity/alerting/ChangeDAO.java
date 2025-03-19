package io.hyperfoil.tools.horreum.entity.alerting;

import static jakarta.persistence.GenerationType.SEQUENCE;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This marks certain run as following a change (regression) in tested criterion.
 * Change is not necessarily a negative one - e.g. improvement in throughput is still
 * a change to facilitate correct testing of subsequent runs.
 * Eventually the change should be manually confirmed (approved) with an explanatory description.
 */
@Entity(name = "Change")
@Table(name = "change")
public class ChangeDAO extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "changeIdGenerator", sequenceName = "change_seq")
    @GeneratedValue(strategy = SEQUENCE, generator = "changeIdGenerator")
    public int id;

    @NotNull
    @ManyToOne
    public VariableDAO variable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    public DatasetDAO dataset;

    @NotNull
    @Column(columnDefinition = "timestamp")
    public Instant timestamp;

    @NotNull
    public boolean confirmed;

    public String description;

    public DatasetDAO.Info getDatasetId() {
        if (dataset != null) {
            return dataset.getInfo();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Change{" +
                "id=" + id +
                ", variable=" + variable.id +
                ", dataset=" + dataset.id + " (" + dataset.run.id + "/" + dataset.ordinal + ")" +
                ", timestamp=" + timestamp +
                ", confirmed=" + confirmed +
                ", description='" + description + '\'' +
                '}';
    }

    public static ChangeDAO fromDatapoint(DataPointDAO dp) {
        ChangeDAO change = new ChangeDAO();
        change.variable = dp.variable;
        change.timestamp = dp.timestamp;
        change.dataset = dp.dataset;
        return change;
    }

}
