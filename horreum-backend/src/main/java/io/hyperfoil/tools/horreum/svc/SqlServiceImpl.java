package io.hyperfoil.tools.horreum.svc;

import java.util.Collections;
import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.JDBCException;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.data.JsonpathValidation;
import io.hyperfoil.tools.horreum.api.data.QueryResult;
import io.hyperfoil.tools.horreum.api.internal.services.SqlService;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class SqlServiceImpl implements SqlService {
    private static final Logger log = Logger.getLogger(SqlServiceImpl.class);

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity identity;

    @Inject
    RoleManager roleManager;

    @ConfigProperty(name = "horreum.debug")
    Optional<Boolean> debug;

    static void setFromException(PersistenceException pe, JsonpathValidation result) {
        result.valid = false;
        if (pe.getCause() instanceof JDBCException) {
            JDBCException je = (JDBCException) pe.getCause();
            result.errorCode = je.getErrorCode();
            result.sqlState = je.getSQLState();
            result.reason = je.getSQLException().getMessage();
            result.sql = je.getSQL();
        } else {
            result.reason = pe.getMessage();
        }
    }

    @PermitAll
    @WithRoles
    @WithToken
    @Override
    public QueryResult queryRunData(int id, String jsonpath, String schemaUri, boolean array) {
        String func = array ? "jsonb_path_query_array" : "jsonb_path_query_first";
        QueryResult result = new QueryResult();
        result.jsonpath = jsonpath;
        try {
            if (schemaUri != null && !schemaUri.isEmpty()) {
                String sqlQuery = "SELECT " + func + "((CASE " +
                        "WHEN rs.type = 0 THEN run.data WHEN rs.type = 1 THEN run.data->rs.key ELSE run.data->(rs.key::integer) END)"
                        +
                        ", (?1)::jsonpath)#>>'{}' FROM run JOIN run_schemas rs ON rs.runid = run.id WHERE id = ?2 AND rs.uri = ?3";
                result.value = String.valueOf(Util.runQuery(em, sqlQuery, jsonpath, id, schemaUri));
            } else {
                String sqlQuery = "SELECT " + func + "(data, (?1)::jsonpath)#>>'{}' FROM run WHERE id = ?2";
                result.value = String.valueOf(Util.runQuery(em, sqlQuery, jsonpath, id));
            }
            result.valid = true;
        } catch (PersistenceException pe) {
            SqlServiceImpl.setFromException(pe, result);
        }
        return result;
    }

    @WithRoles
    @Override
    public QueryResult queryDatasetData(int datasetId, String jsonpath, boolean array, String schemaUri) {
        if (schemaUri != null && schemaUri.isBlank()) {
            schemaUri = null;
        }
        QueryResult result = new QueryResult();
        result.jsonpath = jsonpath;
        try {
            if (schemaUri == null) {
                String func = array ? "jsonb_path_query_array" : "jsonb_path_query_first";
                String sqlQuery = "SELECT " + func + "(data, ?::jsonpath)#>>'{}' FROM dataset WHERE id = ?";
                result.value = String.valueOf(Util.runQuery(em, sqlQuery, jsonpath, datasetId));
            } else {
                // This schema-aware query already assumes that Dataset.data is an array of objects with defined schema
                String schemaQuery = "jsonb_path_query(data, '$[*] ? (@.\"$schema\" == $schema)', ('{\"schema\":\"' || ? || '\"}')::jsonb)";
                String sqlQuery;
                if (!array) {
                    sqlQuery = "SELECT jsonb_path_query_first(" + schemaQuery
                            + ", ?::jsonpath)#>>'{}' FROM dataset WHERE id = ? LIMIT 1";
                } else {
                    sqlQuery = "SELECT jsonb_agg(v)#>>'{}' FROM (SELECT jsonb_path_query(" + schemaQuery
                            + ", ?::jsonpath) AS v FROM dataset WHERE id = ?) AS values";
                }
                result.value = String.valueOf(Util.runQuery(em, sqlQuery, schemaUri, jsonpath, datasetId));
            }
            result.valid = true;
        } catch (PersistenceException pe) {
            SqlServiceImpl.setFromException(pe, result);
        }
        return result;
    }

    @Override
    @PermitAll
    public JsonpathValidation testJsonPath(String jsonpath) {
        if (jsonpath == null) {
            throw ServiceException.badRequest("No query");
        }
        return testJsonPathInternal(jsonpath);
    }

    JsonpathValidation testJsonPathInternal(String jsonpath) {
        jsonpath = jsonpath.trim();
        JsonpathValidation result = new JsonpathValidation();
        result.jsonpath = jsonpath;
        if (jsonpath.startsWith("strict") || jsonpath.startsWith("lax")) {
            result.valid = false;
            result.reason = "Horreum always uses lax (default) jsonpaths.";
            return result;
        }
        if (!jsonpath.startsWith("$")) {
            result.valid = false;
            result.reason = "Jsonpath should start with '$'";
            return result;
        }
        Query query = em.createNativeQuery("SELECT jsonb_path_query_first('{}', ?::jsonpath)::text");
        query.setParameter(1, jsonpath);
        try {
            query.getSingleResult();
            result.valid = true;
        } catch (PersistenceException pe) {
            setFromException(pe, result);
        }
        return result;
    }

    @Override
    @PermitAll
    public String roles(boolean system) {
        if (!debug.orElse(false)) {
            throw ServiceException.notFound("Not available without debug mode.");
        }
        if (identity.isAnonymous()) {
            return "<anonymous>";
        }
        if (system) {
            if (identity.hasRole(Roles.ADMIN)) {
                return roleManager.getDebugQuery(Collections.singletonList(Roles.HORREUM_SYSTEM));
            } else {
                throw ServiceException.forbidden("Only Admin can request system roles");
            }
        }
        return roleManager.getDebugQuery(identity);
    }
}
