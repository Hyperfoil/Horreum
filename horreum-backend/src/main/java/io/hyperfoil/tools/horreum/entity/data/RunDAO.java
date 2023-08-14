package io.hyperfoil.tools.horreum.entity.data;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;

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
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode data;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
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
