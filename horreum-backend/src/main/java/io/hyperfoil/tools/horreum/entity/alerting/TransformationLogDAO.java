package io.hyperfoil.tools.horreum.entity.alerting;

import jakarta.persistence.*;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;

@Entity(name = "TransformationLog")
public class TransformationLogDAO extends PersistentLogDAO {
    @Id
    @SequenceGenerator(name = "transformationlog_id_generator", sequenceName = "transformationlog_id_generator", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transformationlog_id_generator")
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
