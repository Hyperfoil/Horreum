package io.hyperfoil.tools.horreum.entity.changeDetection;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;

/**
 */
@Entity(name = "ChangeDetectionLog")
public class ChangeDetectionLogDAO extends PersistentLogDAO {

   @Id
   @GenericGenerator(
           name = "changedetectionlog_id_generator",
           type = SeqIdGenerator.class,
           parameters = { @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "changedetectionlog_id_generator")
   public Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "variableid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   public VariableDAO variable;

   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode fingerprint;

   public ChangeDetectionLogDAO() {
      super(0, null);
   }

   public ChangeDetectionLogDAO(VariableDAO variable, JsonNode fingerprint, int level, String message) {
      super(level, message);
      this.variable = variable;
      this.fingerprint = fingerprint;
   }

   public VariableDAO getVariable() {
      return variable;
   }

   public JsonNode getFingerprint() {
      return fingerprint;
   }
}
