package io.hyperfoil.tools.horreum.entity.data;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.databind.JsonNode;

@Entity(name = "Action")
public class ActionDAO extends PanacheEntityBase {
   @Id
   @GenericGenerator(
         name = "actionSequence",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = "action_id_seq"),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "actionSequence")
   public Integer id;

   @NotNull
   @Column(name = "event")
   public String event;

   @NotNull
   @Column(name = "type")
   public String type;

   @NotNull
   @Column(name = "config", columnDefinition = "jsonb")
   @Type(JsonBinaryType.class)
   public JsonNode config;

   @NotNull
   @Column(name = "secrets", columnDefinition = "jsonb")
   @Type(JsonBinaryType.class)
   public JsonNode secrets;

   @NotNull
   @Column(name = "test_id")
   public Integer testId;

   @NotNull
   @Transient
   public boolean active = true;

   @NotNull
   @Column(name = "run_always")
   public boolean runAlways;

   public ActionDAO() {
   }
    public ActionDAO(Integer id, String event, String type, JsonNode config, JsonNode secrets,
                     Integer testId, boolean active, boolean runAlways) {
       this.id = id;
       this.event = event;
       this.type = type;
       this.config = config;
       this.secrets = secrets;
       this.testId = testId;
       this.active = active;
       this.runAlways = runAlways;
    }

}
