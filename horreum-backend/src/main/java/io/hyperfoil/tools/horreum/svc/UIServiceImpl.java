package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.data.View;
import io.hyperfoil.tools.horreum.api.services.UIService;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.data.ViewDAO;
import io.hyperfoil.tools.horreum.mapper.ViewMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Collections;

public class UIServiceImpl implements UIService {

    @Inject
    EntityManager em;

    @Inject
    TestServiceImpl testService;

    @Override
    @RolesAllowed("tester")
    @WithRoles
    @Transactional
    public int updateView(int testId, View dto) {
        if (testId <= 0) {
            throw ServiceException.badRequest("Missing test id");
        }
        TestDAO test = testService.getTestForUpdate(testId);
        ViewDAO view = ViewMapper.to(dto);
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
        return view.id;
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
        // the orphan removal doesn't work for some reason, we need to remove if manually
        ViewDAO.deleteById(viewId);
        test.persist();
    }

}
