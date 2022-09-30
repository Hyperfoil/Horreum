package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.hibernate.Hibernate;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.hibernate.type.IntegerType;
import org.hibernate.type.TextType;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.ExperimentService;
import io.hyperfoil.tools.horreum.bus.MessageBus;
import io.hyperfoil.tools.horreum.entity.ExperimentComparison;
import io.hyperfoil.tools.horreum.entity.ExperimentProfile;
import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.experiment.ExperimentConditionModel;
import io.hyperfoil.tools.horreum.experiment.RelativeDifferenceExperimentModel;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.Startup;

@ApplicationScoped
@Startup
public class ExperimentServiceImpl implements ExperimentService {
   private static final Logger log = Logger.getLogger(ExperimentServiceImpl.class);
   private static final Map<String, ExperimentConditionModel> MODELS = Map.of(
         RelativeDifferenceExperimentModel.NAME, new RelativeDifferenceExperimentModel());

   @Inject
   EntityManager em;

   @Inject
   MessageBus messageBus;

   @PostConstruct
   void init() {
      messageBus.subscribe(DataPoint.EVENT_DATASET_PROCESSED, "ExperimentService", DataPoint.DatasetProcessedEvent.class, this::onDatapointsCreated);
   }

   @WithRoles
   @PermitAll
   @Override
   public Collection<ExperimentProfile> profiles(int testId) {
      return ExperimentProfile.list("test_id", testId);
   }

   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public int addOrUpdateProfile(int testId, ExperimentProfile profile) {
      if (profile.selectorLabels == null || profile.selectorLabels.isEmpty()) {
         throw ServiceException.badRequest("Experiment profile must have selector labels defined.");
      } else if (profile.baselineLabels == null || profile.baselineLabels.isEmpty()) {
         throw ServiceException.badRequest("Experiment profile must have baseline labels defined.");
      }
      profile.test = em.getReference(Test.class, testId);
      if (profile.id < 0) {
         profile.id = null;
         profile.persist();
      } else {
         if (profile.test.id != testId) {
            throw ServiceException.badRequest("Test ID does not match");
         }
         em.merge(profile);
      }
      return profile.id;
   }

   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public void deleteProfile(int testId, int profileId) {
      if (!ExperimentProfile.deleteById(profileId)) {
         throw ServiceException.notFound("No experiment profile " + profileId);
      }
   }

   @Override
   public List<ConditionConfig> models() {
      return MODELS.values().stream().map(ExperimentConditionModel::config).collect(Collectors.toList());
   }

   @Override
   @WithRoles
   @Transactional
   public List<ExperimentResult> runExperiments(int datasetId) {
      DataSet dataset = DataSet.findById(datasetId);
      if (dataset == null) {
         throw ServiceException.notFound("No dataset " + datasetId);
      }
      List<ExperimentService.ExperimentResult> results = new ArrayList<>();
      DataSet.Info info = dataset.getInfo();
      runExperiments(info, results::add, logs -> results.add(new ExperimentResult(null, logs, info, Collections.emptyList(), Collections.emptyMap(), null, false)), false);
      return results;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   public void onDatapointsCreated(DataPoint.DatasetProcessedEvent event) {
      // TODO: experiments can use any datasets, including private ones, possibly leaking the information
      runExperiments(event.dataset, result -> messageBus.publish(ExperimentResult.NEW_RESULT, result),
            logs -> logs.forEach(log -> log.persist()), event.notify);
   }

   private void addLog(List<DatasetLog> logs, int testId, int datasetId, int level, String format, Object... args) {
      String msg = args.length == 0 ? format : String.format(format, args);
      log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLog.logLevel(level), testId, datasetId, msg);
      logs.add(new DatasetLog(em.getReference(Test.class, testId), em.getReference(DataSet.class, datasetId),
            level, "experiment", msg));
   }

   private void runExperiments(DataSet.Info info, Consumer<ExperimentResult> resultConsumer, Consumer<List<DatasetLog>> noProfileConsumer, boolean notify) {
      List<DatasetLog> logs = new ArrayList<>();

      Query selectorQuery = em.createNativeQuery("WITH lvalues AS (" +
            "SELECT ep.id AS profile_id, selector_filter, jsonb_array_length(selector_labels) as count, label.name, lv.value " +
            "FROM experiment_profile ep JOIN label ON json_contains(ep.selector_labels, label.name) " +
            "LEFT JOIN label_values lv ON label.id = lv.label_id WHERE ep.test_id = ?1 AND lv.dataset_id = ?2" +
            ") SELECT profile_id, selector_filter, (CASE " +
            "WHEN count > 1 THEN jsonb_object_agg(COALESCE(name, ''), lvalues.value) " +
            "WHEN count = 1 THEN jsonb_agg(lvalues.value) -> 0 " +
            "ELSE '{}'::::jsonb END " +
            ") AS value FROM lvalues GROUP BY profile_id, selector_filter, count");
      @SuppressWarnings("unchecked")
      List<Object[]> selectorRows = selectorQuery.setParameter(1, info.testId).setParameter(2, info.id)
            .unwrap(NativeQuery.class)
            .addScalar("profile_id", IntegerType.INSTANCE)
            .addScalar("selector_filter", TextType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .getResultList();

      List<Integer> matchingProfile = new ArrayList<>();
      Util.evaluateMany(selectorRows, r -> Util.makeFilter((String) r[1]), r -> (JsonNode) r[2], (r, result) -> {
         if (result.asBoolean()) {
            matchingProfile.add((Integer) r[0]);
         }
      }, r -> {
         if (((JsonNode) r[2]).booleanValue()) {
            matchingProfile.add((Integer) r[0]);
         }
      }, (r, ex, code) -> addLog(logs, info.testId, info.id,
            PersistentLog.ERROR, "Selector filter failed: %s Code: %s", ex.getMessage(), code),
         output -> addLog(logs, info.testId, info.id,
            PersistentLog.DEBUG, "Selector filter output: %s", output));
      if (matchingProfile.isEmpty()) {
         addLog(logs, info.testId, info.id, PersistentLog.INFO, "There are no matching experiment profiles.");
         noProfileConsumer.accept(logs);
         return;
      }

      Query baselineQuery = em.createNativeQuery("WITH lvalues AS (" +
            "SELECT ep.id AS profile_id, baseline_filter, jsonb_array_length(baseline_labels) as count, label.name, lv.value, lv.dataset_id " +
            "FROM experiment_profile ep JOIN label ON json_contains(ep.baseline_labels, label.name) " +
            "LEFT JOIN label_values lv ON label.id = lv.label_id " +
            "JOIN dataset ON dataset.id = lv.dataset_id " +
            "WHERE ep.id IN ?1 AND dataset.testid = ?2 " +
            ") SELECT profile_id, baseline_filter, (CASE " +
            "WHEN count > 1 THEN jsonb_object_agg(COALESCE(name, ''), lvalues.value) " +
            "WHEN count = 1 THEN jsonb_agg(lvalues.value) -> 0 " +
            "ELSE '{}'::::jsonb END " +
            ") AS value, dataset_id FROM lvalues GROUP BY profile_id, baseline_filter, dataset_id, count");
      @SuppressWarnings("unchecked")
      List<Object[]> baselineRows = baselineQuery.setParameter(1, matchingProfile).setParameter(2, info.testId)
            .unwrap(NativeQuery.class)
            .addScalar("profile_id", IntegerType.INSTANCE)
            .addScalar("baseline_filter", TextType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .addScalar("dataset_id", IntegerType.INSTANCE)
            .getResultList();

      Map<Integer, List<Integer>> baselines = new HashMap<>();
      Map<Integer, List<DatasetLog>> perProfileLogs = matchingProfile.stream().collect(Collectors.toMap(Function.identity(), id -> new ArrayList<>(logs)));
      Util.evaluateMany(baselineRows, r -> Util.makeFilter((String) r[1]), r -> (JsonNode) r[2], (r, v) -> {
         if (v.asBoolean()) {
            baselines.computeIfAbsent((Integer) r[0], profileId -> new ArrayList<>()).add((Integer) r[3]);
         }
      }, r -> {
         if (((JsonNode) r[2]).asBoolean()) {
            baselines.computeIfAbsent((Integer) r[0], profileId -> new ArrayList<>()).add((Integer) r[3]);
         }
      }, (r, ex, code) -> addLog(perProfileLogs.get((Integer) r[0]), info.testId, (Integer) r[3],
               PersistentLog.ERROR, "Baseline filter failed: %s Code: %s", ex.getMessage(), code),
         output -> perProfileLogs.forEach((profileId, pls)-> addLog(pls, info.testId, info.id,
               PersistentLog.DEBUG, "Baseline filter output: %s", output)));

      Map<Integer, DataPoint> datapoints = DataPoint.<DataPoint>find("dataset_id = ?1", info.id)
            .stream().collect(Collectors.toMap(dp -> dp.variable.id, Function.identity(),
                  // defensive merge: although we should not be able to load any old datapoints
                  // (with identical dataset_id+variable_id combo) these may temporarily appear
                  // hence we let the new one (with higher id) win.
                  (dp1, dp2) -> dp1.id > dp2.id ? dp1 : dp2));

      for (var entry : baselines.entrySet()) {
         List<DatasetLog> profileLogs = perProfileLogs.get(entry.getKey());
         ExperimentProfile profile = ExperimentProfile.findById(entry.getKey());
         Map<Integer, List<DataPoint>> byVar = new HashMap<>();
         List<Integer> variableIds = profile.comparisons.stream().map(ExperimentComparison::getVariableId).collect(Collectors.toList());
         DataPoint.<DataPoint>find("dataset_id IN ?1 AND variable_id IN ?2", Sort.descending("timestamp", "dataset_id"), entry.getValue(), variableIds)
               .stream().forEach(dp -> byVar.computeIfAbsent(dp.variable.id, v -> new ArrayList<>()).add(dp));
         Map<ExperimentComparison, ComparisonResult> results = new HashMap<>();
         for (var comparison : profile.comparisons) {
            Hibernate.initialize(comparison.variable);
            ExperimentConditionModel model = MODELS.get(comparison.model);
            if (model == null) {
               addLog(profileLogs, info.testId, info.id, PersistentLog.ERROR, "Unknown experiment comparison model '%s' for variable %s in profile %s", comparison.model, comparison.variable.name, profile.name);
               continue;
            }
            List<DataPoint> baseline = byVar.get(comparison.getVariableId());
            if (baseline == null) {
               addLog(profileLogs, info.testId, info.id, PersistentLog.INFO, "Baseline for comparison of variable %s in profile %s is empty (datapoints are not present)", comparison.variable.name, profile.name);
               continue;
            }
            DataPoint datapoint = datapoints.get(comparison.getVariableId());
            if (datapoint == null) {
               addLog(profileLogs, info.testId, info.id, PersistentLog.ERROR, "No datapoint for comparison of variable %s in profile %s", comparison.variable.name, profile.name);
               continue;
            }
            results.put(comparison, model.compare(comparison.config, baseline, datapoint));
         }

         Query datasetQuery = em.createNativeQuery("SELECT id, runid as \"runId\", ordinal, testid as \"testId\" FROM dataset WHERE id IN ?1 ORDER BY start DESC");
         SqlServiceImpl.setResultTransformer(datasetQuery, Transformers.aliasToBean(DataSet.Info.class));
         @SuppressWarnings("unchecked") List<DataSet.Info> baseline =
               (List<DataSet.Info>) datasetQuery.setParameter(1, entry.getValue()).getResultList();

         JsonNode extraLabels = (JsonNode) em.createNativeQuery("SELECT COALESCE(jsonb_object_agg(COALESCE(label.name, ''), lv.value), '{}'::::jsonb) AS value " +
               "FROM experiment_profile ep JOIN label ON json_contains(ep.extra_labels, label.name) " +
               "LEFT JOIN label_values lv ON label.id = lv.label_id WHERE ep.id = ?1 AND lv.dataset_id = ?2")
               .setParameter(1, profile.id).setParameter(2, info.id)
               .unwrap(NativeQuery.class)
               .addScalar("value", JsonNodeBinaryType.INSTANCE)
               .getSingleResult();
         Hibernate.initialize(profile.test.name);
         resultConsumer.accept(new ExperimentResult(profile, profileLogs, info, baseline, results, extraLabels, notify));
      }
   }
}
