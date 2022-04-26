package io.hyperfoil.tools.horreum.entity.json;

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

import com.fasterxml.jackson.databind.JsonNode;

@NamedNativeQueries({
   @NamedNativeQuery(
      name = Schema.QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID,
      query = "SELECT te.name, (CASE WHEN te.isarray THEN jsonb_path_query_array(r.data, te.jsonpath::::jsonpath) ELSE jsonb_path_query_first(r.data,te.jsonpath::::jsonpath) END) AS value "
         + " FROM run_schemas rs, transformer t, transformer_extractors te, run r "
         + " WHERE (rs.schemaid = t.schema_id) AND (te.transformer_id = t.id) AND (rs.prefix = '$') "
         + " AND (r.id = ?) AND (rs.schemaid = ?) AND (t.id = ?) "),
   @NamedNativeQuery(
      name = Schema.QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID,
      query = "WITH "
         + " elements AS ( SELECT value FROM jsonb_each((SELECT r.data from run r WHERE r.id =?)) ) "
         + " , prefixes AS ( SELECT value->>'$schema' AS schema_uri, value AS data FROM elements ) "
         + " SELECT te.name, (CASE WHEN te.isarray THEN jsonb_path_query_array(r.data, te.jsonpath::::jsonpath) ELSE jsonb_path_query_first(r.data,te.jsonpath::::jsonpath) END) AS value "
         + " FROM run_schemas rs, transformer t, transformer_extractors te, run r, prefixes p WHERE (p.schema_uri = rs.uri) "
         + " AND (rs.schemaid = t.schema_id) AND (te.transformer_id = t.id) "
         + " AND (r.id = ?) AND (rs.schemaid = ?) AND (t.id = ?) "),
   @NamedNativeQuery(
         name = Schema.QUERY_SCHEMA_BY_RUNID,
         query = "SELECT rs.schemaid, t.id AS transformer_id, rs.prefix FROM run_schemas rs LEFT OUTER JOIN transformer t ON t.schema_id = rs.schemaid WHERE rs.runid = ?1 AND rs.testid = ?2 ORDER BY schemaid, transformer_id")
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
   public static final String QUERY_SCHEMA_BY_RUNID = "Schema.getSchemasForRun";

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
}
