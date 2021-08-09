package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import io.hyperfoil.tools.horreum.api.GrafanaService;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * This service works as a backend for calls from Grafana (using
 * <a href="https://grafana.com/grafana/plugins/simpod-json-datasource">simpod-json-datasource</a>)
 * since Horreum exposes charts as embedded Grafana panels.
 */
@PermitAll
@ApplicationScoped
public class GrafanaServiceImpl implements GrafanaService {
   @Inject
   SqlServiceImpl sqlService;

   @Inject
   SecurityIdentity identity;

   @Inject
   EntityManager em;

   @Context
   HttpServletRequest request;

   @Override
   public Object[] search(Target query) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return Variable.<Variable>listAll().stream().map(v -> String.valueOf(v.id)).toArray();
      }
   }

   @Override
   public List<TimeseriesTarget> query(Query query) {
      if (query == null) {
         throw ServiceException.badRequest("No query");
      } else if (query.range == null || query.range.from == null || query.range.to == null) {
         throw ServiceException.badRequest("Invalid time range");
      }
      List<TimeseriesTarget> result = new ArrayList<>();
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         for (Target target : query.targets) {
            if (target.type != null && !target.type.equals("timeseries")) {
               throw ServiceException.badRequest("Tables are not implemented");
            }
            String tq = target.target;
            Map<String, String> tags = null;
            int semicolon = tq.indexOf(';');
            if (semicolon >= 0) {
               tags = Tags.parseTags(tq.substring(semicolon + 1));
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
            if (tags != null) {
               sql.append(" LEFT JOIN run_tags ON run_tags.runid = datapoint.runid ");
            }
            sql.append(" WHERE variable_id = ?1 AND timestamp BETWEEN ?2 AND ?3 ");
            Tags.addTagQuery(tags, sql, 4);
            sql.append(" ORDER BY timestamp ASC");
            javax.persistence.Query nativeQuery = em.createNativeQuery(sql.toString(), DataPoint.class)
                  .setParameter(1, variableId)
                  .setParameter(2, query.range.from)
                  .setParameter(3, query.range.to);
            Tags.addTagValues(tags, nativeQuery, 4);
            @SuppressWarnings("unchecked")
            List<DataPoint> datapoints = nativeQuery.getResultList();
            for (DataPoint dp : datapoints) {
               tt.datapoints.add(new Number[] { dp.value, dp.timestamp.toEpochMilli(), /* non-standard! */ dp.runId });
            }
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
      Map<String, String> tags = null;
      int semicolon = tq.indexOf(';');
      if (semicolon >= 0) {
         tags = Tags.parseTags(tq.substring(semicolon + 1));
         tq = tq.substring(0, semicolon);
      }
      int variableId = parseVariableId(tq);
      if (variableId < 0) {
         throw ServiceException.badRequest("Query must be variable ID");
      }
      // TODO: use identity forwarded
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         StringBuilder sql = new StringBuilder("SELECT change.* FROM change ");
         if (tags != null) {
            sql.append(" JOIN run_tags ON run_tags.runid = change.runid ");
         }
         sql.append(" WHERE variable_id = ?1 AND timestamp BETWEEN ?2 AND ?3 ");
         Tags.addTagQuery(tags, sql, 4);
         javax.persistence.Query nativeQuery = em.createNativeQuery(sql.toString(), Change.class)
               .setParameter(1, variableId)
               .setParameter(2, query.range.from)
               .setParameter(3, query.range.to);
         Tags.addTagValues(tags, nativeQuery, 4);

         @SuppressWarnings("unchecked")
         List<Change> changes = nativeQuery.getResultList();
         for (Change change : changes) {
            annotations.add(createAnnotation(change));
         }
      }
      return annotations;
   }

   private AnnotationDefinition createAnnotation(Change change) {
      StringBuilder content = new StringBuilder("Variable: ").append(change.variable.name);
      if (change.variable.group != null) {
         content.append(" (group ").append(change.variable.group).append(")");
      }
      content.append("<br>").append(change.description).append("<br>Confirmed: ").append(change.confirmed);
      return new AnnotationDefinition("Change in run " + change.runId, content.toString(), false,
            change.timestamp.toEpochMilli(), 0, new String[0], change.id, change.variable.id, change.runId);
   }

}
