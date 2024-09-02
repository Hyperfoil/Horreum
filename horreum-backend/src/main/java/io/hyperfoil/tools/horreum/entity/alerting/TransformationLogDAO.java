package io.hyperfoil.tools.horreum.entity.alerting;

import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import io.hyperfoil.tools.horreum.entity.CustomSequenceGenerator;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;

@Entity(name = "TransformationLog")
public class TransformationLogDAO extends PersistentLogDAO {
    @Id
    @CustomSequenceGenerator(name = "transformationlog_id_generator", allocationSize = 1)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    public TestDAO test;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "runid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    public RunDAO run;

    public TransformationLogDAO() {
        super(0, null);
    }

    public TransformationLogDAO(TestDAO test, RunDAO run, int level, String message) {
        super(level, message);
        this.test = test;
        this.run = run;
    }

    private int getTestId() {
        return test.id;
    }

    private int getRunId() {
        return run.id;
    }

}
