package io.hyperfoil.tools.horreum.entity.json;

import java.util.Collection;

import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRule;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Entity(name="test")
@RegisterForReflection
public class Test extends PanacheEntityBase {
   public static final String EVENT_NEW = "test/new";
   public static final String EVENT_DELETED = "test/deleted";

   @JsonProperty(required = true)
   @Id
   @SequenceGenerator(
      name = "testSequence",
      sequenceName = "test_id_seq",
      allocationSize = 1,
      initialValue = 10) // skip 10 to account for example-data.sql entries
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "testSequence")
   @Column(name="id")
   public Integer id;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String folder;

   @Column(name="description",unique = false)
   public String description;

   @NotNull
   public String owner;

   @NotNull
   public Access access = Access.PUBLIC;

   @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
   public Collection<TestToken> tokens;

   @Schema(implementation = String[].class)
   @Column(name = "timeline_labels")
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode timelineLabels;

   @Column(name = "timeline_function")
   public String timelineFunction;

   @Schema(implementation = String[].class)
   @Column(name = "fingerprint_labels")
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode fingerprintLabels;

   @Column(name = "fingerprint_filter")
   public String fingerprintFilter;

   @NotNull
   @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.MERGE }, orphanRemoval = true)
   public View defaultView;

   @NotNull
   @OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.MERGE }, orphanRemoval = true, mappedBy = "test")
   @Fetch(FetchMode.SELECT)
   public Collection<View> views;

   public String compareUrl;

   @OneToMany(fetch = FetchType.EAGER)
   @JoinTable(name = "test_transformers", joinColumns = @JoinColumn(name = "test_id"), inverseJoinColumns = @JoinColumn(name = "transformer_id"))
   @Fetch(FetchMode.SELECT)
   public Collection<Transformer> transformers;

   @NotNull
   @Column(columnDefinition = "boolean default true")
   public Boolean notificationsEnabled;

   public void ensureLinked() {
      if (defaultView != null) {
         defaultView.test = this;
         defaultView.ensureLinked();
      }
      if (views != null) views.forEach(v -> v.test = this);
   }
}