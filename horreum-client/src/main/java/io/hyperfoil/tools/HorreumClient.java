package io.hyperfoil.tools;

import java.time.Instant;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;

import io.hyperfoil.tools.auth.KeycloakClientRequestFilter;
import io.hyperfoil.tools.horreum.api.*;
import io.hyperfoil.tools.serializer.CustomYaupProvider;
import io.hyperfoil.tools.serializer.JsonDeserializer;
import io.hyperfoil.tools.serializer.JsonSerializer;
import io.hyperfoil.tools.yaup.json.Json;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.microprofile.client.impl.MpClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.StringTextStar;

import javax.ws.rs.core.UriBuilder;

public class HorreumClient {

    public final AlertingService alertingService;
    public final GrafanaService grafanaService;
    public final HookService hookService;
    public final NotificationService notificationService;
    public final RunService runService;
    public final SchemaService schemaService;
    public final SqlService sqlService;
    public final SubscriptionService subscriptionService;
    public final TestService testService;
    public final UserService userService;

    public HorreumClient(AlertingService alertingService, GrafanaService grafanaService, HookService hookService,
                         NotificationService notificationService, RunService horreumRunService, SchemaService schemaService, SqlService sqlService,
                         SubscriptionService subscriptionService, TestService horreumTestService, UserService userService) {
        this.alertingService = alertingService;
        this.grafanaService = grafanaService;
        this.hookService = hookService;
        this.notificationService = notificationService;
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

        public HorreumClient build() throws IllegalStateException {

            KeycloakClientRequestFilter requestFilter = new KeycloakClientRequestFilter(keycloakUrl,
                    keycloakRealm,
                    horreumUser,
                    horreumPassword,
                    clientId,
                    clientSecret);


            MpClientBuilderImpl clientBuilder = new MpClientBuilderImpl();

            //Custom Json object mapper
            CustomYaupProvider customJsonProvider = new CustomYaupProvider();
            ObjectMapper customJsonMapper = new ObjectMapper();
            SimpleModule customModule = new SimpleModule("customJsonModule", new Version(1, 0, 0, null, "io.hyperfoil.tools", "horreum-client-java"));
            customModule.addSerializer(Json.class, new JsonSerializer());
            customModule.addDeserializer(Json.class, new JsonDeserializer());
            customModule.addSerializer(Instant.class, InstantSerializer.INSTANCE);
            customModule.addDeserializer(Instant.class, InstantDeserializer.INSTANT);

            customJsonMapper.registerModule(customModule);
            customJsonProvider.setMapper(customJsonMapper);

            //Override default ObjectMapper Provider
            clientBuilder.register(customJsonProvider, 100);

            //Register Keycloak Request Filter
            clientBuilder.register(requestFilter);
            // Other MessageBodyReaders/Writers that may not be found by ServiceLoader mechanism
            clientBuilder.register(new StringTextStar());
            clientBuilder.register(new DefaultTextPlain());

            ResteasyClient client = clientBuilder.build();
            ResteasyWebTarget target = client.target(UriBuilder.fromPath(this.horreumUrl));

            return new HorreumClient(
                    target.proxyBuilder(AlertingService.class).build(),
                    target.proxyBuilder(GrafanaService.class).build(),
                    target.proxyBuilder(HookService.class).build(),
                    target.proxyBuilder(NotificationService.class).build(),
                    target.proxyBuilder(RunService.class).build(),
                    target.proxyBuilder(SchemaService.class).build(),
                    target.proxyBuilder(SqlService.class).build(),
                    target.proxyBuilder(SubscriptionService.class).build(),
                    target.proxyBuilder(TestService.class).build(),
                    target.proxyBuilder(UserService.class).build());
        }
    }

}
