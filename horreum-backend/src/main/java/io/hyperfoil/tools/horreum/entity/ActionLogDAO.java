package io.hyperfoil.tools.horreum.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;

@Entity(name = "ActionLog")
public class ActionLogDAO extends PersistentLogDAO {

   @Id
   @GenericGenerator(
           name = "actionlog_id_generator",
           type = SeqIdGenerator.class,
           parameters = { @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "actionlog_id_generator")
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
