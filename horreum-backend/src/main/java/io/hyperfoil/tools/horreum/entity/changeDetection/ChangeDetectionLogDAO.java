package io.hyperfoil.tools.horreum.entity.changeDetection;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.entity.CustomSequenceGenerator;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.Type;

/**
 */
@Entity(name = "ChangeDetectionLog")
public class ChangeDetectionLogDAO extends PersistentLogDAO {

   @Id
   @CustomSequenceGenerator(
         name = "changedetectionlog_id_generator",
         allocationSize = 1
   )
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
