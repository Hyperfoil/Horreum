package io.hyperfoil.tools.horreum.entity.data;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.type.SqlTypes;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;
import static org.hibernate.id.enhanced.SequenceStyleGenerator.SEQUENCE_PARAM;

@NamedQuery(
    name = TestDAO.QUERY_TEST_NAME,
    query = "SELECT name FROM test WHERE id = ?1"
)
@Entity(name="test")
@JsonIgnoreType
public class TestDAO extends PanacheEntityBase {

   public static final String QUERY_TEST_NAME = "TestDAO.getNameById";

   @Id
   @GenericGenerator(
         name = "testIdGenerator",
         type = SeqIdGenerator.class,
         parameters = { @Parameter(name = SEQUENCE_PARAM, value = "test_id_seq"), @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "testIdGenerator")
   @Column(name="id")
   public Integer id;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String folder;

   @Column(name="description",unique = false)
   public String description;

   @ManyToOne(fetch=FetchType.EAGER)
   public DatastoreConfigDAO backendConfig;

   @NotNull
   public String owner;

   @NotNull
   @JdbcTypeCode(SqlTypes.INTEGER)
   public Access access = Access.PUBLIC;

   @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
   public Collection<TestTokenDAO> tokens;

   @Column(name = "timeline_labels")
   @Type(JsonBinaryType.class)
   public JsonNode timelineLabels;

   @Column(name = "timeline_function")
   public String timelineFunction;

   @Column(name = "fingerprint_labels")
   @Type(JsonBinaryType.class)
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