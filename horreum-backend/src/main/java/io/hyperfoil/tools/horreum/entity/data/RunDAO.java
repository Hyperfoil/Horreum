package io.hyperfoil.tools.horreum.entity.data;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.Type;

@Entity(name = "run")
@DynamicUpdate // We don't want to trigger schema analysis when trashing the run
@JsonIgnoreType
public class RunDAO extends ProtectedBaseEntity {
   public static final String EVENT_NEW = "run/new";
   public static final String EVENT_TRASHED = "run/trashed";
   public static final String EVENT_VALIDATED = "run/validated";

   @Id
   @SequenceGenerator(
      name = "runSequence",
      sequenceName = "run_id_seq",
      allocationSize = 1)
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "runSequence")
   public Integer id;

   @NotNull
   @Column(name="start", columnDefinition = "timestamp")
   public Instant start;

   @NotNull
   @Column(name="stop", columnDefinition = "timestamp")
   public Instant stop;

   public String description;

   @NotNull
   public Integer testid;

   @NotNull
   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode data;

   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode metadata;

   @NotNull
   @Column(columnDefinition = "boolean default false")
   public boolean trashed;

   @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
   public Collection<DataSetDAO> datasets;

   @CollectionTable
   @ElementCollection
   public Collection<ValidationErrorDAO> validationErrors;
}
