package io.hyperfoil.tools.horreum.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity(name = "ActionLog")
public class ActionLogDAO extends PersistentLogDAO {

    @Id
    @SequenceGenerator(name = "actionlog_id_generator", sequenceName = "actionlog_id_generator", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "actionlog_id_generator")
    public Long id;

    @NotNull
    public int testId;

    @NotNull
    public String event;

    public String type;

    public ActionLogDAO() {
        super(0, null);
    }

    public ActionLogDAO(int level, int testId, String event, String type, String message) {
        super(level, message);
        this.testId = testId;
        this.event = event;
        this.type = type;
    }
}
