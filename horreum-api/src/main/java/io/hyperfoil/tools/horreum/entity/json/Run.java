package io.hyperfoil.tools.horreum.entity.json;

import io.quarkus.runtime.annotations.RegisterForReflection;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

import javax.persistence.CascadeType;
import javax.persistence.Column;
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
@RegisterForReflection
@DynamicUpdate // We don't want to trigger schema analysis when trashing the run
public class Run extends ProtectedBaseEntity {
   public static final String EVENT_NEW = "run/new";
   public static final String EVENT_TRASHED = "run/trashed";

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

   @NotNull
   @Column(columnDefinition = "boolean default false")
   public boolean trashed;

   @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
   public Collection<DataSet> datasets;
}
