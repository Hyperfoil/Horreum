package io.hyperfoil.tools.horreum.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

@Entity(name = "ActionLog")
public class ActionLogDAO extends PersistentLogDAO {

   @Id
   @GenericGenerator(
           name = "actionlog_id_generator",
           strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
           parameters = {
                   @org.hibernate.annotations.Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
           }
   )
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
