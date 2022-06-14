package io.hyperfoil.tools;

import io.hyperfoil.tools.auth.KeycloakClientRequestFilter;
import io.hyperfoil.tools.horreum.api.*;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.StringTextStar;

import javax.net.ssl.SSLContext;
import javax.ws.rs.core.UriBuilder;
import java.security.NoSuchAlgorithmException;

public class HorreumClient {

    public final AlertingService alertingService;
    public final BannerService bannerService;
    public final DatasetService datasetService;
    public final GrafanaService grafanaService;
    public final HookService hookService;
    public final NotificationService notificationService;
    public final ReportService reportService;
    public final RunService runService;
    public final SchemaService schemaService;
    public final SqlService sqlService;
    public final SubscriptionService subscriptionService;
    public final TestService testService;
    public final UserService userService;

    public HorreumClient(AlertingService alertingService, BannerService bannerService,  DatasetService datasetService, GrafanaService grafanaService, HookService hookService,
                         NotificationService notificationService, ReportService reportService, RunService horreumRunService, SchemaService schemaService, SqlService sqlService,
                         SubscriptionService subscriptionService, TestService horreumTestService, UserService userService) {
        this.alertingService = alertingService;
        this.bannerService = bannerService;
        this.datasetService = datasetService;
        this.grafanaService = grafanaService;
        this.hookService = hookService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.runService = horreumRunService;
        this.schemaService = schemaService;
        this.sqlService = sqlService;
        this.subscriptionService = subscriptionService;
        this.testService = horreumTestService;
        this.userService = userService;
    }

    public static class Builder {
        private String horreumUrl;
        private String keycloakUrl;
        private String keycloakRealm = "horreum";
        private String horreumUser;
        private String horreumPassword;
        private String clientId = "horreum-ui";
        private String clientSecret;

        private ResteasyClientBuilder clientBuilder;

        public Builder() {
        }

        public Builder horreumUrl(String horreumUrl) {
            this.horreumUrl = horreumUrl;
            return this;
        }

        public Builder keycloakUrl(String keycloakUrl) {
            this.keycloakUrl = keycloakUrl;
            return this;
        }

        public Builder keycloakRealm(String keycloakRealm) {
            this.keycloakRealm = keycloakRealm;
            return this;
        }

        public Builder horreumUser(String horreumUser) {
            this.horreumUser = horreumUser;
            return this;
        }

        public Builder horreumPassword(String horreumPassword) {
            this.horreumPassword = horreumPassword;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder resteasyClientBuilder(ResteasyClientBuilder clientBuilder) {
            this.clientBuilder = clientBuilder;
            return this;
        }

        public HorreumClient build() throws IllegalStateException {

            KeycloakClientRequestFilter requestFilter = new KeycloakClientRequestFilter(keycloakUrl,
                    keycloakRealm,
                    horreumUser,
                    horreumPassword,
                    clientId,
                    clientSecret,
                    clientBuilder);


            ResteasyClientBuilderImpl clientBuilder = new ResteasyClientBuilderImpl();

            //Override default ObjectMapper Provider
            clientBuilder.register(new CustomResteasyJackson2Provider(), 100);
            try {
                clientBuilder.sslContext(SSLContext.getDefault());
            } catch (NoSuchAlgorithmException e) {
                // Do nothing
            }

            try {
                clientBuilder.sslContext(SSLContext.getDefault());
            } catch (NoSuchAlgorithmException e) {
                // Do nothing
            }

            //Register Keycloak Request Filter
            clientBuilder.register(requestFilter);
            // Other MessageBodyReaders/Writers that may not be found by ServiceLoader mechanism
            clientBuilder.register(new StringTextStar());
            clientBuilder.register(new DefaultTextPlain());

            ResteasyClient client = clientBuilder.build();
            ResteasyWebTarget target = client.target(UriBuilder.fromPath(this.horreumUrl));

            return new HorreumClient(
                    target.proxyBuilder(AlertingService.class).build(),
                    target.proxyBuilder(BannerService.class).build(),
                    target.proxyBuilder(DatasetService.class).build(),
                    target.proxyBuilder(GrafanaService.class).build(),
                    target.proxyBuilder(HookService.class).build(),
                    target.proxyBuilder(NotificationService.class).build(),
                    target.proxyBuilder(ReportService.class).build(),
                    target.proxyBuilder(RunService.class).build(),
                    target.proxyBuilder(SchemaService.class).build(),
                    target.proxyBuilder(SqlService.class).build(),
                    target.proxyBuilder(SubscriptionService.class).build(),
                    target.proxyBuilder(TestService.class).build(),
                    target.proxyBuilder(UserService.class).build());
        }
    }

}
