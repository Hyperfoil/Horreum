package io.hyperfoil.tools.horreum.entity.data;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Entity(name = "Action")
@RegisterForReflection
public class ActionDAO extends PanacheEntityBase {
   @JsonProperty(required = true)
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
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   @Column(name = "config")
   public JsonNode config;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   @Column(name = "secrets")
   @JsonIgnore
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

    @JsonProperty("secrets")
   public void setSecrets(JsonNode secrets) {
      this.secrets = secrets;
   }

   // Had we called this simply `getSecrets` Quarkus would rewrite (??!!) some property
   // accesses to use of that method
   @JsonProperty("secrets")
   public JsonNode getMaskedSecrets() {
      if (secrets != null && secrets.isObject()) {
         ObjectNode masked = JsonNodeFactory.instance.objectNode();
         secrets.fieldNames().forEachRemaining(name -> {
            masked.put(name, "********");
         });
         return masked;
      } else {
         return JsonNodeFactory.instance.objectNode();
      }
   }
}
