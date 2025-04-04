package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.hyperfoil.tools.horreum.api.alerting.Watch;
import io.hyperfoil.tools.horreum.api.data.TestExport;
import io.hyperfoil.tools.horreum.api.internal.services.SubscriptionService;
import io.hyperfoil.tools.horreum.entity.alerting.WatchDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.mapper.WatchMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
@Startup
public class SubscriptionServiceImpl implements SubscriptionService {

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity identity;

    private static Set<String> merge(Set<String> set, String item) {
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(item);
        return set;
    }

    @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @WithRoles
    @Override
    public Watch getSubscription(int testId) {
        WatchDAO watch = WatchDAO.find("test.id = ?1", testId).firstResult();
        if (watch == null) {
            watch = new WatchDAO();
            watch.test = em.getReference(TestDAO.class, testId);
            watch.teams = Collections.emptyList();
            watch.users = Collections.emptyList();
            watch.optout = Collections.emptyList();
        }
        return WatchMapper.from(watch);
    }

    private static List<String> add(List<String> list, String item) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(item);
        return list;
    }

    @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @WithRoles
    @Transactional
    @Override
    public List<String> addUserOrTeam(int testId, String userOrTeam) {
        if (userOrTeam == null) {
            throw ServiceException.badRequest("Missing user/team");
        } else if (userOrTeam.startsWith("\"") && userOrTeam.endsWith("\"") && userOrTeam.length() > 2) {
            userOrTeam = userOrTeam.substring(1, userOrTeam.length() - 1);
        }
        boolean isTeam = true;
        boolean isOptout = false;
        if (userOrTeam.startsWith("!")) {
            userOrTeam = userOrTeam.substring(1);
            isOptout = true;
        }
        String username = identity.getPrincipal().getName();
        if (userOrTeam.equals("__self") || userOrTeam.equals(username)) {
            userOrTeam = username;
            isTeam = false;
        } else if (!userOrTeam.endsWith("-team") || !identity.getRoles().contains(userOrTeam)) {
            throw ServiceException.badRequest("Wrong user/team: " + userOrTeam);
        }
        if (isTeam && isOptout) {
            throw ServiceException.badRequest("Cannot opt-out team: use remove");
        }
        WatchDAO watch = WatchDAO.find("test.id", testId).firstResult();
        if (watch == null) {
            watch = new WatchDAO();
            watch.test = em.getReference(TestDAO.class, testId);
        }
        if (isOptout) {
            watch.optout = add(watch.optout, userOrTeam);
            if (watch.users != null) {
                watch.users.remove(userOrTeam);
            }
        } else if (isTeam) {
            watch.teams = add(watch.teams, userOrTeam);
        } else {
            watch.users = add(watch.users, userOrTeam);
            if (watch.optout != null) {
                watch.optout.remove(userOrTeam);
            }
        }
        watch.persist();
        return currentWatches(watch);
    }

    @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @WithRoles
    @Transactional
    @Override
    public List<String> removeUserOrTeam(int testId, String userOrTeam) {
        if (userOrTeam == null) {
            throw ServiceException.badRequest("Missing user/team");
        } else if (userOrTeam.startsWith("\"") && userOrTeam.endsWith("\"") && userOrTeam.length() > 2) {
            userOrTeam = userOrTeam.substring(1, userOrTeam.length() - 1);
        }
        WatchDAO watch = WatchDAO.find("test.id", testId).firstResult();
        if (watch == null) {
            return Collections.emptyList();
        }
        boolean isOptout = false;
        if (userOrTeam.startsWith("!")) {
            isOptout = true;
            userOrTeam = userOrTeam.substring(1);
        }
        String username = identity.getPrincipal().getName();
        if (userOrTeam.equals("__self") || userOrTeam.equals(username)) {
            if (isOptout) {
                if (watch.optout != null) {
                    watch.optout.remove(userOrTeam);
                }
            } else if (watch.users != null) {
                watch.users.remove(username);
            }
        } else if (userOrTeam.endsWith("-team") && identity.getRoles().contains(userOrTeam)) {
            if (isOptout) {
                throw ServiceException.badRequest("Team cannot be opted out.");
            }
            if (watch.teams != null) {
                watch.teams.remove(userOrTeam);
            }
        } else {
            throw ServiceException.badRequest("Wrong user/team: " + userOrTeam);
        }
        watch.persist();
        return currentWatches(watch);
    }

    @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @WithRoles
    @Transactional
    @Override
    public void updateSubscription(int testId, Watch dto) {
        WatchDAO watch = WatchMapper.to(dto);
        WatchDAO existing = WatchDAO.find("test.id", testId).firstResult();
        if (existing == null) {
            watch.id = null;
            watch.test = em.getReference(TestDAO.class, testId);
            if (watch.users == null) {
                watch.users = Collections.emptyList();
            }
            if (watch.teams == null) {
                watch.teams = Collections.emptyList();
            }
            if (watch.optout == null) {
                watch.optout = Collections.emptyList();
            }
            watch.persistAndFlush();
        } else {
            existing.users = watch.users;
            existing.optout = watch.optout;
            existing.teams = watch.teams;
            existing.persistAndFlush();
        }
    }

    private List<String> currentWatches(WatchDAO watch) {
        ArrayList<String> own = new ArrayList<>(identity.getRoles());
        String username = identity.getPrincipal().getName();
        own.add(username);
        ArrayList<String> all = new ArrayList<>();
        if (watch.teams != null) {
            all.addAll(watch.teams);
        }
        if (watch.users != null) {
            all.addAll(watch.users);
        }
        all.retainAll(own);
        if (watch.optout != null && watch.optout.contains(username)) {
            all.add("!" + username);
        }
        return all;
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onTestDelete(int testId) {
        var subscriptions = WatchDAO.list("test.id = ?1", testId);
        Log.infof("Deleting %d subscriptions for test %d", subscriptions.size(), testId);
        for (var subscription : subscriptions) {
            subscription.delete();
        }
    }

    void exportSubscriptions(TestExport test) {
        test.subscriptions = getSubscription(test.id);
    }

    void importSubscriptions(TestExport test) {
        WatchDAO watch = WatchMapper.to(test.subscriptions);
        watch.test = em.getReference(TestDAO.class, test.id);
        if (watch.id != null && WatchDAO.findById(watch.id) == null) {
            watch.id = null;
            watch.persist();
        } else {
            em.merge(watch);
        }
    }
}
