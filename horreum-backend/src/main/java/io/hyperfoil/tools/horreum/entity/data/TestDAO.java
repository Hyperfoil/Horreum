package io.hyperfoil.tools.horreum.entity.data;

import java.util.Collection;

import io.hyperfoil.tools.horreum.api.data.Access;
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
import javax.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Entity(name="test")
@RegisterForReflection
public class TestDAO extends PanacheEntityBase {
   public static final String EVENT_NEW = "test/new";
   public static final String EVENT_DELETED = "test/deleted";

   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "testIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = "test_id_seq"),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "testIdGenerator")
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
   public Collection<TestTokenDAO> tokens;

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
   @OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.ALL }, orphanRemoval = true, mappedBy = "test")
   @Fetch(FetchMode.SELECT)
   public Collection<ViewDAO> views;

   public String compareUrl;

   @OneToMany(fetch = FetchType.EAGER)
   @JoinTable(name = "test_transformers", joinColumns = @JoinColumn(name = "test_id"), inverseJoinColumns = @JoinColumn(name = "transformer_id"))
   @Fetch(FetchMode.SELECT)
   public Collection<TransformerDAO> transformers;

   @NotNull
   @Column(columnDefinition = "boolean default true")
   public Boolean notificationsEnabled;

   public void ensureLinked() {
      if (views != null) {
         views.forEach(v -> {
            v.test = this;
            v.ensureLinked();
         });
      }
      if (tokens != null) {
         tokens.forEach(t -> {
            t.test = this;
         });
      }
   }
}