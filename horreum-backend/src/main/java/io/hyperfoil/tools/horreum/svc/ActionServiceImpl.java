package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.action.ActionPlugin;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.AllowedSite;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.TestExport;
import io.hyperfoil.tools.horreum.api.internal.services.ActionService;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.entity.ActionLogDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.ActionDAO;
import io.hyperfoil.tools.horreum.entity.data.AllowedSiteDAO;
import io.hyperfoil.tools.horreum.mapper.ActionMapper;
import io.hyperfoil.tools.horreum.mapper.AllowedSiteMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

@ApplicationScoped
@Startup
public class ActionServiceImpl implements ActionService {

    @Inject
    Instance<ActionPlugin> actionPlugins;
    Map<String, ActionPlugin> plugins;

    @Inject
    EntityManager em;

    @Inject
    Vertx vertx;

    @Inject
    TestServiceImpl testService;

    @PostConstruct()
    public void postConstruct() {
        plugins = actionPlugins.stream().collect(Collectors.toMap(ActionPlugin::type, Function.identity()));
    }

    private void executeActions(ActionEvent event, int testId, Object payload, boolean notify) {
        List<ActionDAO> actions = getActions(event, testId);
        if (actions.isEmpty()) {
            new ActionLogDAO(PersistentLogDAO.DEBUG, testId, event.name(), null, "No actions found.").persist();
            return;
        }
        for (ActionDAO action : actions) {
            if (!notify && !action.runAlways) {
                Log.debugf("Ignoring action for event %s in test %d, type %s as this event should not notfiy", event, testId,
                        action.type);
                continue;
            }
            try {
                ActionPlugin plugin = plugins.get(action.type);
                if (plugin == null) {
                    Log.errorf("No plugin for action type %s", action.type);
                    new ActionLogDAO(PersistentLogDAO.ERROR, testId, event.name(), action.type,
                            "No plugin for action type " + action.type).persist();
                    continue;
                }
                plugin.execute(action.config, action.secrets, payload).subscribe()
                        .with(item -> {
                        }, throwable -> logActionError(testId, event.name(), action.type, throwable));
            } catch (Exception e) {
                Log.errorf(e, "Failed to invoke action %d", action.id);
                new ActionLogDAO(PersistentLogDAO.ERROR, testId, event.name(), action.type,
                        "Failed to invoke: " + e.getMessage()).persist();
                new ActionLogDAO(PersistentLogDAO.DEBUG, testId, event.name(), action.type,
                        "Configuration: <pre>\n<code>" + action.config.toPrettyString() +
                                "\n<code></pre>Payload: <pre>\n<code>"
                                + Util.OBJECT_MAPPER.valueToTree(payload).toPrettyString() +
                                "</code>\n</pre>")
                        .persist();
            }
        }
    }

    void logActionError(int testId, String event, String type, Throwable throwable) {
        Log.errorf("Error executing action '%s' for event %s on test %d: %s: %s",
                type, event, testId, throwable.getClass().getName(), throwable.getMessage());
        Util.executeBlocking(vertx, CachedSecurityIdentity.ANONYMOUS, Uni.createFrom().item(() -> {
            doLogActionError(testId, event, type, throwable);
            return null;
        })).subscribe().with(item -> {
        }, t -> {
            Log.error("Cannot log error in action!", t);
            Log.error("Logged error: ", throwable);
        });
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void doLogActionError(int testId, String event, String type, Throwable throwable) {
        new ActionLogDAO(PersistentLogDAO.ERROR, testId, event, type, throwable.getMessage()).persist();
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onNewTest(Test test) {
        executeActions(ActionEvent.TEST_NEW, test.id, test, true);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onTestDelete(int testId) {
        ActionDAO.delete("testId", testId);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onNewRun(Run run) {
        Integer testId = run.testid;
        executeActions(ActionEvent.RUN_NEW, testId, run, true);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onNewChange(Change.Event changeEvent) {
        int testId = em.createQuery("SELECT testid FROM run WHERE id = ?1", Integer.class)
                .setParameter(1, changeEvent.dataset.runId).getResultStream().findFirst().orElse(-1);
        executeActions(ActionEvent.CHANGE_NEW, testId, changeEvent, changeEvent.notify);
    }

    void validate(Action action) {
        ActionPlugin plugin = plugins.get(action.type);
        if (plugin == null) {
            throw ServiceException.badRequest("Unknown hook type " + action.type);
        }
        plugin.validate(action.config, action.secrets);
    }

    @RolesAllowed(Roles.ADMIN)
    @WithRoles
    @Transactional
    @Override
    public Action addGlobalAction(Action action) {
        if (action == null) {
            throw ServiceException.badRequest("Send action as request body");
        }
        if (action.testId != null && action.testId > 0) {
            throw ServiceException.badRequest("Global action cannot have a Test associated with it");
        }

        // ensure the id is null as we are creating a new record
        action.id = null;
        action.testId = -1;
        return createAction(action);
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public Action addAction(Action action) {
        if (action == null) {
            throw ServiceException.badRequest("Send action as request body");
        }

        // just ensure the test exists
        if (action.testId == null || action.testId <= 0) {
            throw ServiceException.badRequest("Missing test id");
        }
        testService.getTestForUpdate(action.testId);

        // ensure the id is null as we are creating a new record
        action.id = null;
        return createAction(action);
    }

    Action createAction(Action action) {
        action.config = ensureNotNull(action.config);
        action.secrets = ensureNotNull(action.secrets);
        validate(action);
        ActionDAO actionEntity = ActionMapper.to(action);
        if (action.id == null) {
            actionEntity.persist();
            action.id = actionEntity.id;
        } else {
            merge(actionEntity);
        }

        return action;
    }

    ObjectNode ensureNotNull(ObjectNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? JsonNodeFactory.instance.objectNode() : node;
    }

    void merge(ActionDAO action) {
        if (action.secrets == null || !action.secrets.isObject()) {
            action.secrets = JsonNodeFactory.instance.objectNode();
        }
        if (!action.secrets.path("modified").asBoolean(false)) {
            ActionDAO old = ActionDAO.findById(action.id);
            action.secrets = old == null ? JsonNodeFactory.instance.objectNode() : old.secrets;
        } else {
            ((ObjectNode) action.secrets).remove("modified");
        }
        em.merge(action);
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public Action updateAction(Action dto) {
        if (dto.id == null || dto.id <= 0) {
            throw ServiceException.badRequest("Cannot update an action with no id");
        }
        if (dto.testId == null || dto.testId <= 0) {
            throw ServiceException.badRequest("Missing test id");
        }

        // just ensure the test exists
        testService.getTestForUpdate(dto.testId);
        validate(dto);

        ActionDAO action = ActionMapper.to(dto);
        if (!action.active) {
            ActionDAO.deleteById(action.id);
            return null;
        } else {
            merge(action);
        }

        return ActionMapper.from(action);
    }

    @RolesAllowed(Roles.ADMIN)
    @WithRoles
    @Override
    public Action getAction(int id) {
        ActionDAO action = ActionDAO.find("id", id).firstResult();
        return ActionMapper.from(action);
    }

    @WithRoles
    @RolesAllowed(Roles.TESTER)
    @Transactional
    @Override
    public void deleteAction(int id) {
        // check you have rights on that test
        ActionDAO action = ActionDAO.<ActionDAO> findByIdOptional(id)
                .orElseThrow(() -> ServiceException.notFound("Action with id " + id + " not found!"));

        if (action.testId == null || action.testId <= 0) {
            throw ServiceException.badRequest("Cannot delete global action: missing test id");
        }

        // just ensure the test exists
        testService.getTestForUpdate(action.testId);
        ActionDAO.deleteById(id);
    }

    @WithRoles
    @RolesAllowed(Roles.ADMIN)
    @Transactional
    @Override
    public void deleteGlobalAction(int id) {
        ActionDAO action = ActionDAO.<ActionDAO> findByIdOptional(id)
                .orElseThrow(() -> ServiceException.notFound("Action with id " + id + " not found!"));

        if (action.testId > 0) {
            throw ServiceException.badRequest("Cannot delete test action");
        }

        ActionDAO.delete("id", id);
    }

    public List<ActionDAO> getActions(ActionEvent event, int testId) {
        if (testId < 0) {
            return ActionDAO.find("event = ?1", event).list();
        } else {
            return ActionDAO.find("event = ?1 and (testId = ?2 or testId < 0)", event, testId).list();
        }
    }

    @RolesAllowed(Roles.ADMIN)
    @WithRoles
    @Override
    public List<Action> listActions(Integer limit, Integer page, String sort, SortDirection direction) {
        Sort.Direction sortDirection = direction == null ? null : Sort.Direction.valueOf(direction.name());
        PanacheQuery<ActionDAO> query = ActionDAO.find("testId < 0", Sort.by(sort).direction(sortDirection));
        if (limit != null && page != null) {
            query = query.page(Page.of(page, limit));
        }
        return query.list().stream().map(ActionMapper::from).collect(Collectors.toList());
    }

    @RolesAllowed({ Roles.ADMIN, Roles.TESTER })
    @WithRoles
    @Override
    public List<Action> getTestActions(int testId) {
        List<ActionDAO> testActions = ActionDAO.list("testId", testId);
        return testActions.stream().map(ActionMapper::from).collect(Collectors.toList());
    }

    @PermitAll
    @Override
    public List<AllowedSite> allowedSites() {
        List<AllowedSiteDAO> sites = AllowedSiteDAO.listAll();
        return sites.stream().map(AllowedSiteMapper::from).collect(Collectors.toList());
    }

    @RolesAllowed(Roles.ADMIN)
    @WithRoles
    @Transactional
    @Override
    public AllowedSite addSite(String prefix) {
        AllowedSiteDAO p = new AllowedSiteDAO();
        // FIXME: fetchival stringifies the body into JSON string :-/
        p.prefix = Util.destringify(prefix);
        em.persist(p);
        return AllowedSiteMapper.from(p);
    }

    @RolesAllowed(Roles.ADMIN)
    @WithRoles
    @Transactional
    @Override
    public void deleteSite(long id) {
        AllowedSiteDAO.delete("id", id);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onNewExperimentResult(ExperimentService.ExperimentResult result) {
        executeActions(ActionEvent.EXPERIMENT_RESULT_NEW, result.profile.testId, result, result.notify);
    }

    void exportTest(TestExport test) {
        List<Action> actions = new ArrayList<>();
        for (ActionDAO action : ActionDAO.<ActionDAO> list("testId", test.id)) {
            Action a = ActionMapper.from(action);
            action.secrets = JsonNodeFactory.instance.objectNode();
            actions.add(a);
        }
        test.actions = actions;
    }

    void importTest(TestExport test) {
        for (Action a : test.actions) {
            ActionDAO action = ActionMapper.to(a);
            if (action.id == null || action.id <= 0 || ActionDAO.findById(action.id) == null) {
                action.id = null;
                action.persist();
            } else
                em.merge(action);
        }
    }
}
