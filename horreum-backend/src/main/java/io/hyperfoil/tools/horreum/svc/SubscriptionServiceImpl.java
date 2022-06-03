package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import io.hyperfoil.tools.horreum.api.SubscriptionService;
import io.hyperfoil.tools.horreum.entity.alerting.Watch;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;

@ApplicationScoped
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

   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @WithRoles
   @Override
   public Map<Integer, Set<String>> all(String folder) {
      // TODO: do all of this in single obscure PSQL query
      String username = identity.getPrincipal().getName();
      List<Watch> personal = Watch.list("?1 IN elements(users)", username);
      List<Watch> optout = Watch.list("?1 IN elements(optout)", username);
      Set<String> teams = identity.getRoles().stream().filter(role -> role.endsWith("-team")).collect(Collectors.toSet());
      List<Watch> team = Watch.list("FROM watch w LEFT JOIN w.teams teams WHERE teams IN ?1", teams);
      Map<Integer, Set<String>> result = new HashMap<>();
      personal.forEach(w -> result.compute(w.test.id, (i, set) -> merge(set, username)));
      optout.forEach(w -> result.compute(w.test.id, (i, set) -> merge(set, "!" + username)));
      team.forEach(w -> result.compute(w.test.id, (i, set) -> {
         Set<String> nset = new HashSet<>(w.teams);
         nset.retainAll(teams);
         if (set != null) {
            nset.addAll(set);
         }
         return nset;
      }));
      @SuppressWarnings("unchecked")
      Stream<Integer> results = em.createNativeQuery("SELECT id FROM test WHERE COALESCE(folder, '') = COALESCE((?1)::::text, '')")
            .setParameter(1, folder).getResultStream();
      results.forEach(id -> result.putIfAbsent(id, Collections.emptySet()));
      return result;
   }

   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @WithRoles
   @Override
   public Watch get(int testId) {
      Watch watch = Watch.find("test.id = ?1", testId).firstResult();
      if (watch == null) {
         watch = new Watch();
         watch.test = em.getReference(Test.class, testId);
         watch.teams = Collections.emptyList();
         watch.users = Collections.emptyList();
         watch.optout = Collections.emptyList();
      }
      return watch;
   }

   private static List<String> add(List<String> list, String item) {
      if (list == null) {
         list = new ArrayList<>();
      }
      list.add(item);
      return list;
   }

   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
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
      Watch watch = Watch.find("testid", testId).firstResult();
      if (watch == null) {
         watch = new Watch();
         watch.test = em.getReference(Test.class, testId);
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

   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @WithRoles
   @Transactional
   @Override
   public List<String> removeUserOrTeam(int testId, String userOrTeam) {
      if (userOrTeam == null) {
         throw ServiceException.badRequest("Missing user/team");
      } else if (userOrTeam.startsWith("\"") && userOrTeam.endsWith("\"") && userOrTeam.length() > 2) {
         userOrTeam = userOrTeam.substring(1, userOrTeam.length() - 1);
      }
      Watch watch = Watch.find("testid", testId).firstResult();
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

   @RolesAllowed({Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @WithRoles
   @Transactional
   @Override
   public void update(int testId, Watch watch) {
      Watch existing = Watch.find("testid", testId).firstResult();
      if (existing == null) {
         watch.id = null;
         watch.test = em.getReference(Test.class, testId);
         watch.persistAndFlush();
      } else {
         existing.users = watch.users;
         existing.optout = watch.optout;
         existing.teams = watch.teams;
         existing.persistAndFlush();
      }
   }

   private List<String> currentWatches(Watch watch) {
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

   @ConsumeEvent(value = Test.EVENT_DELETED, blocking = true)
   @Transactional
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   public void onTestDelete(Test test) {
      for (Watch w : Watch.<Watch>find("testid = ?1", test.id).list()) {
         w.delete();
      }
   }
}
