package io.hyperfoil.tools.horreum.entity.json;

import io.hyperfoil.tools.horreum.entity.converter.InstantSerializer;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.annotations.Type;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@Entity(name = "run")
@RegisterForReflection
public class Run extends ProtectedBaseEntity {
   public static final String EVENT_NEW = "run/new";
   public static final String EVENT_TRASHED = "run/trashed";
   public static final String EVENT_MISSING_VALUES = "run/missing_value";
   public static final String EVENT_TAGS_CREATED = "run/tags_created";

   @Id
   @SequenceGenerator(
      name = "runSequence",
      sequenceName = "run_id_seq",
      allocationSize = 1)
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "runSequence")
   public Integer id;

   @NotNull
   @Column(name="start", columnDefinition = "timestamp")
   @JsonbTypeDeserializer(InstantSerializer.class)
   @JsonbTypeSerializer(InstantSerializer.class)
   public Instant start;

   @NotNull
   @Column(name="stop", columnDefinition = "timestamp")
   @JsonbTypeDeserializer(InstantSerializer.class)
   @JsonbTypeSerializer(InstantSerializer.class)
   public Instant stop;

   public String description;

   @NotNull
   public Integer testid;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public Json data;

   @NotNull
   @Column(columnDefinition = "boolean default false")
   public boolean trashed;

   public static class TagsEvent {
      public final int runId;
      public final String tags;

      public TagsEvent(int runId, String tags) {
         this.runId = runId;
         this.tags = tags;
      }
   }
}
