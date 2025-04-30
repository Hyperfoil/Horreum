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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.alerting.DataPoint;
import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;
import io.hyperfoil.tools.horreum.api.data.TestExport;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.*;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLogDAO;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.experiment.ExperimentConditionModel;
import io.hyperfoil.tools.horreum.experiment.RelativeDifferenceExperimentModel;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasetLogMapper;
import io.hyperfoil.tools.horreum.mapper.DatasetMapper;
import io.hyperfoil.tools.horreum.mapper.ExperimentProfileMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.Startup;

@ApplicationScoped
@Startup
public class ExperimentServiceImpl implements ExperimentService {
    private static final Map<String, ExperimentConditionModel> MODELS = Map.of(
            RelativeDifferenceExperimentModel.NAME, new RelativeDifferenceExperimentModel());

    @Inject
    EntityManager em;
    @Inject
    ServiceMediator mediator;

    @Inject
    TransactionManager tm;

    @WithRoles
    @PermitAll
    @Override
    public Collection<ExperimentProfile> profiles(int testId) {
        List<ExperimentProfileDAO> profiles = ExperimentProfileDAO.list("test.id", testId);
        return profiles.stream().map(ExperimentProfileMapper::from).collect(Collectors.toList());
    }

    @WithRoles
    @RolesAllowed(Roles.TESTER)
    @Transactional
    @Override
    public int addOrUpdateProfile(int testId, ExperimentProfile dto) {
        if (dto.selectorLabels == null || dto.selectorLabels.isEmpty()) {
            throw ServiceException.badRequest("Experiment profile must have selector labels defined.");
        } else if (dto.baselineLabels == null || dto.baselineLabels.isEmpty()) {
            throw ServiceException.badRequest("Experiment profile must have baseline labels defined.");
        }
        ExperimentProfileDAO profile = ExperimentProfileMapper.to(dto);
        profile.test = em.getReference(TestDAO.class, testId);
        if (profile.id == null || profile.id < 1) {
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
        if (!ExperimentProfileDAO.deleteById(profileId)) {
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
        DatasetDAO dataset = DatasetDAO.findById(datasetId);
        if (dataset == null) {
            throw ServiceException.notFound("No dataset " + datasetId);
        }
        List<ExperimentService.ExperimentResult> results = new ArrayList<>();
        Dataset.Info info = DatasetMapper.fromInfo(dataset.getInfo());
        runExperiments(info, results::add, logs -> results.add(
                new ExperimentResult(null, logs.stream().map(DatasetLogMapper::from).collect(Collectors.toList()),
                        info, Collections.emptyList(),
                        Collections.emptyMap(),
                        null, false)),
                false);
        return results;
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onDatapointsCreated(DataPoint.DatasetProcessedEvent event) {
        // TODO: experiments can use any datasets, including private ones, possibly leaking the information
        runExperiments(event.dataset,
                result -> Util.registerTxSynchronization(tm,
                        value -> mediator.publishEvent(AsyncEventChannels.EXPERIMENT_RESULT_NEW, event.dataset.testId, result)),
                logs -> logs.forEach(log -> log.persist()), event.notify);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onTestDeleted(int testId) {
        // we need to iterate in order to cascade the operation
        for (var profile : ExperimentProfileDAO.list("test.id", testId)) {
            profile.delete();
        }
    }

    private void addLog(List<DatasetLogDAO> logs, int testId, int datasetId, int level, String format, Object... args) {
        String msg = args.length == 0 ? format : format.formatted(args);
        Log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLogDAO.logLevel(level), testId, datasetId, msg);
        logs.add(new DatasetLogDAO(em.getReference(TestDAO.class, testId), em.getReference(DatasetDAO.class, datasetId),
                level, "experiment", msg));
    }

    private void runExperiments(Dataset.Info info, Consumer<ExperimentResult> resultConsumer,
            Consumer<List<DatasetLogDAO>> noProfileConsumer, boolean notify) {
        List<DatasetLogDAO> logs = new ArrayList<>();

        NativeQuery<Object[]> selectorQuery = em.unwrap(Session.class).createNativeQuery(
                """
                        WITH lvalues AS (
                           SELECT ep.id AS profile_id, selector_filter, jsonb_array_length(selector_labels) as count, label.name, lv.value
                           FROM experiment_profile ep
                           JOIN label ON json_contains(ep.selector_labels, label.name)
                           LEFT JOIN label_values lv ON label.id = lv.label_id
                           WHERE ep.test_id = ?1
                              AND lv.dataset_id = ?2
                        )
                        SELECT profile_id, selector_filter, (
                           CASE
                              WHEN count > 1 THEN jsonb_object_agg(COALESCE(name, ''), lvalues.value)
                              WHEN count = 1 THEN jsonb_agg(lvalues.value) -> 0
                              ELSE '{}'::jsonb END
                        ) AS value
                        FROM lvalues
                        GROUP BY profile_id, selector_filter, count
                        """,
                Object[].class);
        List<Object[]> selectorRows = selectorQuery.setParameter(1, info.testId).setParameter(2, info.id)
                .addScalar("profile_id", StandardBasicTypes.INTEGER)
                .addScalar("selector_filter", StandardBasicTypes.TEXT)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .getResultList();

        List<Integer> matchingProfile = new ArrayList<>();
        Util.evaluateWithCombinationFunction(selectorRows, r -> Util.makeFilter((String) r[1]), r -> (JsonNode) r[2],
                (r, result) -> {
                    if (result.asBoolean()) {
                        matchingProfile.add((Integer) r[0]);
                    }
                }, r -> {
                    if (((JsonNode) r[2]).booleanValue()) {
                        matchingProfile.add((Integer) r[0]);
                    }
                }, (r, ex, code) -> addLog(logs, info.testId, info.id,
                        PersistentLogDAO.ERROR, "Selector filter failed: %s Code: %s", ex.getMessage(), code),
                output -> addLog(logs, info.testId, info.id,
                        PersistentLogDAO.DEBUG, "Selector filter output: %s", output));
        if (matchingProfile.isEmpty()) {
            addLog(logs, info.testId, info.id, PersistentLogDAO.INFO, "There are no matching experiment profiles.");
            noProfileConsumer.accept(logs);
            return;
        }

        NativeQuery<Object[]> baselineQuery = em.unwrap(Session.class).createNativeQuery(
                """
                        WITH lvalues AS (
                           SELECT ep.id AS profile_id, baseline_filter, jsonb_array_length(baseline_labels) as count, label.name, lv.value, lv.dataset_id
                           FROM experiment_profile ep
                           JOIN label ON json_contains(ep.baseline_labels, label.name)
                           LEFT JOIN label_values lv ON label.id = lv.label_id
                           JOIN dataset ON dataset.id = lv.dataset_id
                           WHERE ep.id IN ?1
                           AND dataset.testid = ?2
                        )
                        SELECT profile_id, baseline_filter,
                           (CASE
                              WHEN count > 1 THEN jsonb_object_agg(COALESCE(name, ''), lvalues.value)
                              WHEN count = 1 THEN jsonb_agg(lvalues.value) -> 0
                              ELSE '{}'::jsonb END
                           ) AS value,
                           dataset_id
                        FROM lvalues
                        GROUP BY profile_id, baseline_filter, dataset_id, count
                        """,
                Object[].class);
        List<Object[]> baselineRows = baselineQuery.setParameter(1, matchingProfile).setParameter(2, info.testId)
                .addScalar("profile_id", StandardBasicTypes.INTEGER)
                .addScalar("baseline_filter", StandardBasicTypes.TEXT)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .addScalar("dataset_id", StandardBasicTypes.INTEGER)
                .getResultList();

        Map<Integer, List<Integer>> baselines = new HashMap<>();
        Map<Integer, List<DatasetLogDAO>> perProfileLogs = matchingProfile.stream()
                .collect(Collectors.toMap(Function.identity(), id -> new ArrayList<>(logs)));
        Util.evaluateWithCombinationFunction(baselineRows, r -> Util.makeFilter((String) r[1]), r -> (JsonNode) r[2],
                (r, v) -> {
                    if (v.asBoolean()) {
                        baselines.computeIfAbsent((Integer) r[0], profileId -> new ArrayList<>()).add((Integer) r[3]);
                    }
                }, r -> {
                    if (((JsonNode) r[2]).asBoolean()) {
                        baselines.computeIfAbsent((Integer) r[0], profileId -> new ArrayList<>()).add((Integer) r[3]);
                    }
                }, (r, ex, code) -> addLog(perProfileLogs.get((Integer) r[0]), info.testId, (Integer) r[3],
                        PersistentLogDAO.ERROR, "Baseline filter failed: %s Code: %s", ex.getMessage(), code),
                output -> perProfileLogs.forEach((profileId, pls) -> addLog(pls, info.testId, info.id,
                        PersistentLogDAO.DEBUG, "Baseline filter output: %s", output)));

        Map<Integer, DataPointDAO> datapoints = DataPointDAO.<DataPointDAO> find("dataset.id = ?1", info.id)
                .stream().collect(Collectors.toMap(dp -> dp.variable.id, Function.identity(),
                        // defensive merge: although we should not be able to load any old datapoints
                        // (with identical dataset_id+variable_id combo) these may temporarily appear
                        // hence we let the new one (with higher id) win.
                        (dp1, dp2) -> dp1.id > dp2.id ? dp1 : dp2));

        for (var entry : baselines.entrySet()) {
            List<DatasetLogDAO> profileLogs = perProfileLogs.get(entry.getKey());
            ExperimentProfileDAO profile = ExperimentProfileDAO.findById(entry.getKey());
            Map<Integer, List<DataPointDAO>> byVar = new HashMap<>();
            List<Integer> variableIds = profile.comparisons.stream().map(ExperimentComparisonDAO::getVariableId)
                    .collect(Collectors.toList());
            DataPointDAO
                    .<DataPointDAO> find("dataset.id IN ?1 AND variable.id IN ?2", Sort.descending("timestamp", "dataset.id"),
                            entry.getValue(), variableIds)
                    .stream().forEach(dp -> byVar.computeIfAbsent(dp.variable.id, v -> new ArrayList<>()).add(dp));
            Map<String, ComparisonResult> results = new HashMap<>();
            for (var comparison : profile.comparisons) {
                Hibernate.initialize(comparison.variable);
                ExperimentConditionModel model = MODELS.get(comparison.model);
                if (model == null) {
                    addLog(profileLogs, info.testId, info.id, PersistentLogDAO.ERROR,
                            "Unknown experiment comparison model '%s' for variable %s in profile %s", comparison.model,
                            comparison.variable.name, profile.name);
                    continue;
                }
                List<DataPointDAO> baseline = byVar.get(comparison.getVariableId());
                if (baseline == null) {
                    addLog(profileLogs, info.testId, info.id, PersistentLogDAO.INFO,
                            "Baseline for comparison of variable %s in profile %s is empty (datapoints are not present)",
                            comparison.variable.name, profile.name);
                    continue;
                }
                DataPointDAO datapoint = datapoints.get(comparison.getVariableId());
                if (datapoint == null) {
                    addLog(profileLogs, info.testId, info.id, PersistentLogDAO.ERROR,
                            "No datapoint for comparison of variable %s in profile %s", comparison.variable.name, profile.name);
                    continue;
                }
                results.put(comparison.variable.name, model.compare(comparison.config, baseline, datapoint));
            }

            org.hibernate.query.Query<Dataset.Info> datasetQuery = em.unwrap(Session.class).createQuery(
                    "SELECT id, run.id, ordinal, testid FROM dataset WHERE id IN ?1 ORDER BY start DESC", Dataset.Info.class);
            datasetQuery.setTupleTransformer(
                    (tuples, aliases) -> new Dataset.Info((int) tuples[0], (int) tuples[1], (int) tuples[2], (int) tuples[3]));
            List<Dataset.Info> baseline = datasetQuery.setParameter(1, entry.getValue()).getResultList();

            JsonNode extraLabels = (JsonNode) em.createNativeQuery("""
                    SELECT COALESCE(jsonb_object_agg(COALESCE(label.name, ''), lv.value), '{}'::jsonb) AS value
                    FROM experiment_profile ep
                    JOIN label ON json_contains(ep.extra_labels, label.name)
                    LEFT JOIN label_values lv ON label.id = lv.label_id
                    WHERE ep.id = ?1
                       AND lv.dataset_id = ?2
                    """).setParameter(1, profile.id).setParameter(2, info.id)
                    .unwrap(NativeQuery.class)
                    .addScalar("value", JsonBinaryType.INSTANCE)
                    .getSingleResult();
            Hibernate.initialize(profile.test.name);
            ExperimentResult result = new ExperimentResult(ExperimentProfileMapper.from(profile),
                    profileLogs.stream().map(DatasetLogMapper::from).collect(Collectors.toList()),
                    info, baseline, results, extraLabels, notify);
            mediator.newExperimentResult(result);
            resultConsumer.accept(result);
        }
    }

    void exportTest(TestExport test) {
        test.experiments = ExperimentProfileDAO.<ExperimentProfileDAO> list("test.id", test.id)
                .stream().map(ExperimentProfileMapper::from).collect(Collectors.toList());
    }

    void importTest(TestExport test) {
        for (ExperimentProfile ep : test.experiments) {
            ExperimentProfileDAO profile = ExperimentProfileMapper.to(ep);
            profile.test = em.getReference(TestDAO.class, ep.testId);
            if (ep.id != null && ExperimentProfileDAO.findById(ep.id) != null) {
                em.merge(profile);
            } else {
                profile.id = null;
                if (profile.test == null) {
                    profile.test = em.getReference(TestDAO.class, test.id);
                }
                profile.persist();
            }
        }
    }
}
