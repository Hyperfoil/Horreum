package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.GrafanaService;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.hyperfoil.tools.horreum.server.WithRoles;

/**
 * This service works as a backend for calls from Grafana (using
 * <a href="https://grafana.com/grafana/plugins/simpod-json-datasource">simpod-json-datasource</a>)
 * since Horreum exposes charts as embedded Grafana panels.
 */
@PermitAll
@ApplicationScoped
public class GrafanaServiceImpl implements GrafanaService {
   @Inject
   EntityManager em;

   private final List<String> allowedOrigins = new ArrayList<>();

   @PostConstruct
   void init() {
      Config config = ConfigProvider.getConfig();
      String baseUrl = config.getValue("horreum.url", String.class);
      allowedOrigins.add(getOrigin(baseUrl));
      if (baseUrl.contains("//localhost")) {
         // allow live-coding port
         int colonIndex = baseUrl.indexOf(':', 8);
         allowedOrigins.add(baseUrl.substring(0, colonIndex < 0 ? baseUrl.length() : colonIndex) + ":" + config.getValue("quarkus.quinoa.dev-server-port", int.class));
      }
      config.getOptionalValue("horreum.internal.url", String.class).ifPresent(url -> allowedOrigins.add(getOrigin(url)));
      config.getOptionalValue("horreum.grafana.url", String.class).ifPresent(url -> allowedOrigins.add(getOrigin(url)));
   }

   private String getOrigin(String baseUrl) {
      int slashIndex = baseUrl.indexOf('/', 8); // skip http(s)://
      return baseUrl.substring(0, slashIndex < 0 ? baseUrl.length() : slashIndex);
   }

   @WithRoles
   @Override
   public String[] search(Target query) {
      return Variable.<Variable>listAll().stream().map(v -> String.valueOf(v.id)).toArray(String[]::new);
   }

   @WithRoles
   @Override
   public List<TimeseriesTarget> query(Query query) {
      if (query == null) {
         throw ServiceException.badRequest("No query");
      } else if (query.range == null || query.range.from == null || query.range.to == null) {
         throw ServiceException.badRequest("Invalid time range");
      }
      List<TimeseriesTarget> result = new ArrayList<>();
      for (Target target : query.targets) {
         if (target.type != null && !target.type.equals("timeseries")) {
            throw ServiceException.badRequest("Tables are not implemented");
         }
         String tq = target.target;
         JsonNode fingerprint = null;
         int semicolon = tq.indexOf(';');
         if (semicolon >= 0) {
            fingerprint = Util.parseFingerprint(tq.substring(semicolon + 1));
            tq = tq.substring(0, semicolon);
         }
         int variableId = parseVariableId(tq);
         if (variableId < 0) {
            throw ServiceException.badRequest("Target must be variable ID");
         }
         Variable variable = Variable.findById(variableId);
         String variableName = String.valueOf(variableId);
         if (variable != null) {
            variableName = variable.name;
         }
         TimeseriesTarget tt = new TimeseriesTarget();
         tt.target = variableName;
         tt.variableId = variableId;
         result.add(tt);

         StringBuilder sql = new StringBuilder("SELECT datapoint.* FROM datapoint ");
         if (fingerprint != null) {
            sql.append("LEFT JOIN fingerprint fp ON fp.dataset_id = datapoint.dataset_id ");
         }
         sql.append(" WHERE variable_id = ?1 AND timestamp BETWEEN ?2 AND ?3 ");
         if (fingerprint != null) {
            sql.append("AND json_equals(fp.fingerprint, (?4)::::jsonb) ");
         }
         sql.append("ORDER BY timestamp ASC");
         javax.persistence.Query nativeQuery = em.createNativeQuery(sql.toString(), DataPoint.class)
               .setParameter(1, variableId)
               .setParameter(2, query.range.from)
               .setParameter(3, query.range.to);
         if (fingerprint != null) {
            nativeQuery.setParameter(4, fingerprint.toString());
         }
         @SuppressWarnings("unchecked")
         List<DataPoint> datapoints = nativeQuery.getResultList();
         for (DataPoint dp : datapoints) {
            tt.datapoints.add(new Number[] { dp.value, dp.timestamp.toEpochMilli(), /* non-standard! */ dp.dataset.id });
         }
      }
      return result;
   }

   private int parseVariableId(String target) {
      int variableId;
      try {
         variableId = Integer.parseInt(target);
      } catch (NumberFormatException e) {
         // TODO: support test name/variable name?
         variableId = -1;
      }
      return variableId;
   }

   @Override
   public Response annotations(@HeaderParam("Origin") String origin) {
      Response.ResponseBuilder response = Response.ok()
            .header("Access-Control-Allow-Headers", "accept, content-type")
            .header("Access-Control-Allow-Methods", "POST")
            .header("Vary", "Origin");
      for (String allowed : allowedOrigins) {
         if (allowed.equals(origin)) {
            return response.header("Access-Control-Allow-Origin", allowed).build();
         }
      }
      return response.header("Access-Control-Allow-Origin", allowedOrigins.get(0)).build();
   }

   @Override
   @WithRoles
   public List<AnnotationDefinition> annotations(AnnotationsQuery query) {
      if (query == null) {
         throw ServiceException.badRequest("No query");
      } else if (query.range == null || query.range.from == null || query.range.to == null) {
         throw ServiceException.badRequest("Invalid time range");
      }
      // Note that annotations are per-dashboard, not per-panel:
      // https://github.com/grafana/grafana/issues/717
      List<AnnotationDefinition> annotations = new ArrayList<>();
      String tq = query.annotation.query;
      JsonNode fingerprint = null;
      int semicolon = tq.indexOf(';');
      if (semicolon >= 0) {
         fingerprint = Util.parseFingerprint(tq.substring(semicolon + 1));
         tq = tq.substring(0, semicolon);
      }
      int variableId = parseVariableId(tq);
      if (variableId < 0) {
         throw ServiceException.badRequest("Query must be variable ID");
      }
      StringBuilder sql = new StringBuilder("SELECT change.* FROM change ");
      if (fingerprint != null) {
         sql.append(" JOIN fingerprint fp ON fp.dataset_id = change.dataset_id ");
      }
      sql.append(" WHERE variable_id = ?1 AND timestamp BETWEEN ?2 AND ?3 ");
      if (fingerprint != null) {
         sql.append("AND json_equals(fp.fingerprint, (?4)::::jsonb)");
      }
      javax.persistence.Query nativeQuery = em.createNativeQuery(sql.toString(), Change.class)
            .setParameter(1, variableId)
            .setParameter(2, query.range.from)
            .setParameter(3, query.range.to);
      if (fingerprint != null) {
         nativeQuery.setParameter(4, fingerprint.toString());
      }

      @SuppressWarnings("unchecked")
      List<Change> changes = nativeQuery.getResultList();
      for (Change change : changes) {
         annotations.add(createAnnotation(change));
      }
      return annotations;
   }

   private AnnotationDefinition createAnnotation(Change change) {
      StringBuilder content = new StringBuilder("Variable: ").append(change.variable.name);
      if (change.variable.group != null) {
         content.append(" (group ").append(change.variable.group).append(")");
      }
      content.append("<br>").append(change.description).append("<br>Confirmed: ").append(change.confirmed);
      return new AnnotationDefinition("Change in run " + change.dataset.run.id + "/" + change.dataset.ordinal, content.toString(), false,
            change.timestamp.toEpochMilli(), 0, new String[0], change.id, change.variable.id, change.dataset.run.id, change.dataset.ordinal);
   }

}
