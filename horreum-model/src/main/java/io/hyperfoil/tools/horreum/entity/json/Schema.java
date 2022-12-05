package io.hyperfoil.tools.horreum.entity.json;

import java.util.Collection;
import java.util.List;

import io.hyperfoil.tools.horreum.entity.ValidationError;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@NamedNativeQueries({
   @NamedNativeQuery(
      name = Schema.QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID,
      query = "SELECT te.name, (" +
            "CASE WHEN te.isarray THEN jsonb_path_query_array(r.data, te.jsonpath::::jsonpath) " +
            "ELSE jsonb_path_query_first(r.data,te.jsonpath::::jsonpath) END) AS value " +
            "FROM run r, transformer t " +
            "JOIN transformer_extractors te ON te.transformer_id = t.id " +
            "WHERE r.id = ?1 AND t.id = ?2"
   ),
   @NamedNativeQuery(
      name = Schema.QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID,
      query = "SELECT te.name, (" +
            "CASE WHEN te.isarray THEN jsonb_path_query_array((CASE WHEN ?4 = 0 THEN r.data ELSE r.metadata END)->?3, te.jsonpath::::jsonpath) " +
            "ELSE jsonb_path_query_first((CASE WHEN ?4 = 0 THEN r.data ELSE r.metadata END)->?3, te.jsonpath::::jsonpath) END) AS value " +
            "FROM run r, transformer t " +
            "JOIN transformer_extractors te ON te.transformer_id = t.id " +
            "WHERE r.id = ?1 AND t.id = ?2"
   ),
   @NamedNativeQuery(
         name = Schema.QUERY_TRANSFORMER_TARGETS,
         query = "SELECT rs.type, rs.key, t.id as transformer_id, rs.uri, rs.source FROM run_schemas rs " +
               "LEFT JOIN transformer t ON t.schema_id = rs.schemaid AND t.id IN (SELECT transformer_id FROM test_transformers WHERE test_id = rs.testid) " +
               "WHERE rs.runid = ?1 ORDER BY transformer_id NULLS LAST, type, key"
         )
})

@Entity
@RegisterForReflection
@Table(
      name = "schema",
      uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "uri"})
)
public class Schema extends ProtectedBaseEntity {

   public static final String QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID = "Schema.getFirstLevelExtractorsByRunIDTransIDSchemaID";
   public static final String QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID = "Schema.getSecondLevelExtractorsByRunIDTransIDSchemaID";
   public static final String QUERY_TRANSFORMER_TARGETS = "Schema.queryTransformerTargets";
   public static final int TYPE_1ST_LEVEL = 0;
   public static final int TYPE_2ND_LEVEL = 1;
   public static final int TYPE_ARRAY_ELEMENT = 2;

   @JsonProperty(required = true)
   @Id
   @SequenceGenerator(
      name = "schemaSequence",
      sequenceName = "schema_id_seq",
      allocationSize = 1)
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schemaSequence")
   public Integer id;

   @NotNull
   public String uri;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String description;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode schema;

   public static class ValidationEvent {
      public int id; // context = run/dataset depends on event name
      public Collection<ValidationError> errors;

      public ValidationEvent(int id, Collection<ValidationError> errors) {
         this.id = id;
         this.errors = errors;
      }
   }
}
