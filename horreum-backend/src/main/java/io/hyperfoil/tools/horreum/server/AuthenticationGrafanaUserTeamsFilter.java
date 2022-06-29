package io.hyperfoil.tools.horreum.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.svc.UserServiceImpl;
import io.hyperfoil.tools.horreum.svc.Util;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@ApplicationScoped
public class AuthenticationGrafanaUserTeamsFilter {

    private static final Logger log = Logger.getLogger(AuthenticationGrafanaUserTeamsFilter.class.getName());

    private static final String GRAFANA_USER = "grafana_user";

    private static final String TEAMS = "horreum.teams";

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.token.issuer")
    Optional<String> issuer;

    @Inject
    SecurityIdentity identity;

    @Inject @RestClient
    Provider<GrafanaClient> grafana;

    @ConfigProperty(name = "horreum.grafana.url")
    Optional<String> grafanaBaseUrl;

    @Inject
    UserServiceImpl userService;

    public void init(@Observes Router router) {
        log.info("init method called, handler added to route");

        router.get("/*").blockingHandler(rc -> {
            doHorreumAuthorizationFilter(rc);
            doUserTeamsFilter(rc);
            doGrafanaUserFilter(rc);
            rc.next();
        });
    }
    void doHorreumAuthorizationFilter(RoutingContext rc) {
        try {
            String authorization = rc.request().getHeader(HttpHeaders.AUTHORIZATION);
            HttpServerRequest req = rc.request();
            HttpServerResponse res = rc.response();
            if (authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                int payloadStart = authorization.indexOf('.', 7);
                int payloadEnd = authorization.indexOf('.', payloadStart + 1);
                if (payloadStart > 0 && payloadEnd > 0 && payloadStart < payloadEnd) {
                    // okay, looks like JWT token
                    String payload = authorization.substring(payloadStart + 1, payloadEnd);
                    JsonNode payloadObject = Util.toJsonNode(Base64.getDecoder().decode(payload));
                    if (payloadObject == null) {
                        res.setStatusCode(403);
                        res.write("Invalid authorization token");
                        return;
                    }
                    String iss = payloadObject.path("iss").asText();
                    if (iss == null || iss.isBlank()) {
                        res.setStatusCode(403);
                        res.write("Authorization token does not contain issuer ('iss') claim.");
                        return;
                    }
                    if (issuer.isPresent()) {
                        if (issuer.get().equals("any")) {
                            // any issuer matches
                        } else if (!issuer.get().equals(iss)) {
                            replyWrongIss(res, iss);
                            return ;
                        }
                    } else if (!authServerUrl.equals(iss)) {
                        replyWrongIss(res, iss);
                        return ;
                    }
                    //return ;
                }
                req.headers()
                    .remove(HttpHeaders.AUTHORIZATION)
                    .add(TokenInterceptor.TOKEN_HEADER, authorization.substring(7));
            }
        } catch (Exception e){
            log.errorf(e, "Failed to execute HorreumAuthorizationFilter block.");
        }
    }

    @ActivateRequestContext
    void doUserTeamsFilter(RoutingContext rc){
        try {
            if (identity == null || identity.isAnonymous()) {
                // ignore anonymous access
                return;
            }

            Set<String> teams = identity.getRoles().stream().filter(r -> r.endsWith("-team")).collect(Collectors.toSet());
            String username = identity.getPrincipal().getName();
            HttpServerRequest req = rc.request();
            if (req.cookies() != null) {
                OUTER: for (Cookie cookie : req.cookies()) {
                    if (cookie.getName().equals(TEAMS)) {
                        int userEndIndex = cookie.getValue().indexOf('!');
                        if (userEndIndex < 0 || !cookie.getValue().substring(0, userEndIndex).equals(username)) {
                            // cookie belongs to another user
                            break;
                        }
                        String[] cookieTeams = cookie.getValue().substring(userEndIndex + 1).split("\\+");
                        if (cookieTeams.length == teams.size()) {
                            for (String team : cookieTeams) {
                                if (!teams.contains(team)) {
                                    break OUTER;
                                }
                            }
                            // teams in cookie match identity
                            return;
                        } else {
                            break; // OUTER
                        }
                    }
                }
            }
            userService.cacheUserTeams(username, teams);
            // Cookie API does not allow to set SameSite attribute
            rc.response().headers()
                .add("Set-Cookie", TEAMS + "=" + username + "!" + String.join("+", teams) + ";path=/;SameSite=Lax");
        } catch (Exception e) {
            log.errorf(e, "Failed to execute UserTeamsFilter block.");
        }
    }

    @ActivateRequestContext
    void doGrafanaUserFilter(RoutingContext rc){
        try {
            if (grafanaBaseUrl.orElse("").isEmpty()) {
                return;
            }
            if (identity == null) {
                return;
            }
            if (!(identity.getPrincipal() instanceof JWTCallerPrincipal)) {
                // ignore anonymous access
                log.debug("Anonymous access, ignoring.");
                return;
            }
            JWTCallerPrincipal principal = (JWTCallerPrincipal) identity.getPrincipal();
            String email = principal.getClaim("email");
            if (email == null || email.isEmpty()) {
                String username = principal.getName();
                if (username == null) {
                    log.debug("Missing email and username, ignoring.");
                    return;
                } else {
                    email = username + "@horreum";
                }
            }
            HttpServerRequest req = rc.request();
            if (req.cookies() != null) {
                for (Cookie cookie : req.cookies()) {
                    if (cookie.getName().equals(GRAFANA_USER) && email.equals(cookie.getValue())) {
                        log.debugf("%s already has cookie, ignoring.", email);
                        return;
                    }
                }
            }
            GrafanaClient.UserInfo userInfo = null;
            try {
                userInfo = grafana.get().lookupUser(email);
                log.debugf("User %s exists!", email);
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() == 404) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    String password = String.format("_%X%X%X!", random.nextLong(), random.nextLong(), random.nextLong());
                    userInfo = new GrafanaClient.UserInfo(email, email, email, password, 1);
                    try {
                        grafana.get().createUser(userInfo);
                        log.infof("Created Grafana user %s (%s)", userInfo.login, userInfo.email);
                    } catch (WebApplicationException e2) {
                        if (e2.getResponse().getStatus() == 412) {
                            log.infof("This request did not create user %s due to a mid-air collision.", userInfo.login);
                        } else {
                            log.errorf(e2, "Failed to create user %s", email);
                            userInfo = null;
                        }
                    }
                } else {
                    log.errorf(e, "Failed to fetch user %s", email);
                }
            } catch (ProcessingException e) {
                log.debug("Grafana client failed with exception, ignoring.", e);
                return;
            }
            if (userInfo != null) {
                // Cookie API does not allow to set SameSite attribute
                // res.addCookie(new Cookie(GRAFANA_USER, email));
                // The cookie is to expire in 1 minute to handle Grafana restarts
                HttpServerResponse res = rc.response();
                res.putHeader("Set-Cookie", GRAFANA_USER + "=" + email + ";max-age=60;path=/;SameSite=Lax");
            }
        } catch (Exception e){
            log.errorf(e, "Failed to execute GafanaUserFilter section.");
        }
    }
    private void replyWrongIss(HttpServerResponse res, String iss) throws IOException {
        res.setStatusCode(403);
        res.write("Authorization token has issuer '" + iss + "' but this is not the expected issuer; you have probably received the token from a wrong URL. Please login into Horreum Web UI and check the login URL used.");
    }
}
