package io.hyperfoil.tools.horreum.entity.data;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.JsonNode;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;
import static org.hibernate.id.enhanced.SequenceStyleGenerator.SEQUENCE_PARAM;

@NamedNativeQueries({
   @NamedNativeQuery(
      name = SchemaDAO.QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID,
      query = "SELECT te.name, (" +
            "CASE WHEN te.isarray THEN jsonb_path_query_array(r.data, te.jsonpath::jsonpath) " +
            "ELSE jsonb_path_query_first(r.data,te.jsonpath::jsonpath) END) AS value " +
            "FROM run r, transformer t " +
            "JOIN transformer_extractors te ON te.transformer_id = t.id " +
            "WHERE r.id = ?1 AND t.id = ?2"
   ),
   @NamedNativeQuery(
      name = SchemaDAO.QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID,
      query = "SELECT te.name, (" +
            "CASE WHEN te.isarray THEN jsonb_path_query_array((CASE WHEN ?4 = 0 THEN r.data ELSE r.metadata END)->?3, te.jsonpath::jsonpath) " +
            "ELSE jsonb_path_query_first((CASE WHEN ?4 = 0 THEN r.data ELSE r.metadata END)->?3, te.jsonpath::jsonpath) END) AS value " +
            "FROM run r, transformer t " +
            "JOIN transformer_extractors te ON te.transformer_id = t.id " +
            "WHERE r.id = ?1 AND t.id = ?2"
   ),
   @NamedNativeQuery(
         name = SchemaDAO.QUERY_TRANSFORMER_TARGETS,
         query = "SELECT rs.type, rs.key, t.id as transformer_id, rs.uri, rs.source FROM run_schemas rs " +
               "LEFT JOIN transformer t ON t.schema_id = rs.schemaid AND t.id IN (SELECT transformer_id FROM test_transformers WHERE test_id = rs.testid) " +
               "WHERE rs.runid = ?1 ORDER BY transformer_id NULLS LAST, type, key"
         )
})

@Entity(name = "Schema")
@Table(
      name = "schema",
      uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "uri"})
)
@JsonIgnoreType
public class SchemaDAO extends ProtectedBaseEntity {

   public static final String QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID = "Schema.getFirstLevelExtractorsByRunIDTransIDSchemaID";
   public static final String QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID = "Schema.getSecondLevelExtractorsByRunIDTransIDSchemaID";
   public static final String QUERY_TRANSFORMER_TARGETS = "Schema.queryTransformerTargets";
   public static final int TYPE_1ST_LEVEL = 0;
   public static final int TYPE_2ND_LEVEL = 1;
   public static final int TYPE_ARRAY_ELEMENT = 2;

   @Id
   @GenericGenerator(
         name = "schemaIdGenerator",
         type = SeqIdGenerator.class,
         parameters = { @Parameter(name = SEQUENCE_PARAM, value = "schema_id_seq"), @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "schemaIdGenerator")
   public Integer id;

   @NotNull
   public String uri;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String description;

   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode schema;

}
