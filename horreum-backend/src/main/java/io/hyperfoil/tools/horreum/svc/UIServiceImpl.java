package io.hyperfoil.tools.horreum.svc;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.hyperfoil.tools.horreum.api.data.View;
import io.hyperfoil.tools.horreum.api.internal.services.UIService;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.data.ViewDAO;
import io.hyperfoil.tools.horreum.mapper.ViewMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;

public class UIServiceImpl implements UIService {

    @Inject
    EntityManager em;

    @Inject
    TestServiceImpl testService;

    @Inject
    DatasetServiceImpl datasetService;

    @Override
    @RolesAllowed("tester")
    @WithRoles
    @Transactional
    public View updateView(View dto) {
        if (dto.testId <= 0) {
            throw ServiceException.badRequest("Missing test id on view");
        }
        return doUpdate(testService.getTestForUpdate(dto.testId), ViewMapper.to(dto));
    }

    @Override
    @RolesAllowed({ Roles.ADMIN, Roles.TESTER })
    @WithRoles
    @Transactional
    public void createViews(List<View> views) {
        if (views.isEmpty() || views.get(0).testId <= 0) {
            throw ServiceException.badRequest("Missing test id on view");
        }
        TestDAO test = testService.getTestForUpdate(views.get(0).testId);
        for (View view : views) {
            doUpdate(test, ViewMapper.to(view));
        }
    }

    private View doUpdate(TestDAO test, ViewDAO view) {
        view.ensureLinked();
        view.test = test;
        if (view.id == null || view.id < 0) {
            view.id = null;
            view.persist();
        } else {
            view = em.merge(view);
            int viewId = view.id;
            test.views.removeIf(v -> v.id == viewId);
        }
        test.views.add(view);
        test.persist();
        em.flush();

        // update datasets views
        datasetService.calcDatasetViewsByTestAndView(test.id, view.id);
        return ViewMapper.from(view);
    }

    @Override
    @WithRoles
    @Transactional
    public void deleteView(int testId, int viewId) {
        TestDAO test = testService.getTestForUpdate(testId);
        if (test.views == null) {
            test.views = Collections.singleton(new ViewDAO("Default", test));
        } else if (test.views.stream().anyMatch(v -> v.id == viewId && "Default".equals(v.name))) {
            throw ServiceException.badRequest("Cannot remove default view.");
        }
        if (!test.views.removeIf(v -> v.id == viewId)) {
            throw ServiceException.badRequest("Test does not contain this view!");
        }
        // remove dataset views records linked to this view
        em.createNativeQuery("DELETE FROM dataset_view WHERE view_id = ?1").setParameter(1, viewId).executeUpdate();
        // the orphan removal doesn't work for some reason, we need to remove if manually
        ViewDAO.deleteById(viewId);
        test.persist();
    }

    @Override
    @PermitAll
    @WithRoles
    @Transactional
    public List<View> getViews(int testId) {
        if (testId <= 0) {
            throw ServiceException.badRequest("Missing test id");
        }

        TestDAO test = TestDAO.findById(testId);

        if (test == null) {
            throw ServiceException.badRequest("Test not found with id: ".concat(Integer.toString(testId)));
        }

        return ViewDAO.<ViewDAO> find("test.id", testId)
                .stream().map(ViewMapper::from).collect(Collectors.toList());
    }

}
