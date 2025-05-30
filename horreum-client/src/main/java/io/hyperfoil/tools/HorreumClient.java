package io.hyperfoil.tools;

import static io.hyperfoil.tools.horreum.api.services.ConfigService.KEYCLOAK_BOOTSTRAP_URL;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.StringTextStar;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.tools.auth.HorreumApiKeyAuthentication;
import io.hyperfoil.tools.auth.KeycloakClientRequestFilter;
import io.hyperfoil.tools.horreum.api.client.RunService;
import io.hyperfoil.tools.horreum.api.internal.services.ActionService;
import io.hyperfoil.tools.horreum.api.internal.services.AlertingService;
import io.hyperfoil.tools.horreum.api.internal.services.BannerService;
import io.hyperfoil.tools.horreum.api.internal.services.ChangesService;
import io.hyperfoil.tools.horreum.api.internal.services.NotificationService;
import io.hyperfoil.tools.horreum.api.internal.services.ReportService;
import io.hyperfoil.tools.horreum.api.internal.services.SqlService;
import io.hyperfoil.tools.horreum.api.internal.services.SubscriptionService;
import io.hyperfoil.tools.horreum.api.internal.services.UIService;
import io.hyperfoil.tools.horreum.api.services.ConfigService;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.api.services.UserService;

public class HorreumClient implements Closeable {
    private final ResteasyClient client;
    public final ActionService actionService;
    public final AlertingService alertingService;
    public final BannerService bannerService;
    public final ChangesService changesService;
    public final ConfigService configService;
    public final DatasetService datasetService;
    public final ExperimentService experimentService;
    public final NotificationService notificationService;
    public final ReportService reportService;
    public final RunServiceExtension runService;
    public final SchemaService schemaService;
    public final SqlService sqlService;
    public final SubscriptionService subscriptionService;
    public final TestService testService;
    public final UserService userService;
    public final UIService uiService;

    private HorreumClient(ResteasyClient client,
            ActionService actionService, AlertingService alertingService, BannerService bannerService,
            ChangesService changesService, ConfigService configService,
            DatasetService datasetService, ExperimentService experimentService, NotificationService notificationService,
            ReportService reportService, RunServiceExtension runServiceExtension, SchemaService schemaService,
            SqlService sqlService, SubscriptionService subscriptionService, TestService horreumTestService,
            UserService userService, UIService uiService) {
        this.client = client;
        this.alertingService = alertingService;
        this.bannerService = bannerService;
        this.changesService = changesService;
        this.configService = configService;
        this.datasetService = datasetService;
        this.experimentService = experimentService;
        this.actionService = actionService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.runService = runServiceExtension;
        this.schemaService = schemaService;
        this.sqlService = sqlService;
        this.subscriptionService = subscriptionService;
        this.testService = horreumTestService;
        this.userService = userService;
        this.uiService = uiService;
    }

    @Override
    public void close() {
        client.close();
    }

    public static class Builder {
        private String horreumUrl;
        private String horreumUser;
        private String horreumPassword;
        private String horreumApiKey;
        private SSLContext sslContext;

        public Builder() {
        }

        public Builder horreumUrl(String horreumUrl) {
            this.horreumUrl = horreumUrl;
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

        public Builder horreumApiKey(String key) {
            this.horreumApiKey = key;
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder sslContext(String certFilePath) {
            String type = "X.509";
            String alias = "horreum";
            String protocol = "TLS";
            try {
                InputStream fis = new FileInputStream(certFilePath);
                CertificateFactory cf = CertificateFactory.getInstance(type);
                Certificate cert = cf.generateCertificate(fis);
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                keyStore.setCertificateEntry(alias, cert);
                tmf.init(keyStore);
                this.sslContext = SSLContext.getInstance(protocol);
                this.sslContext.init(null, tmf.getTrustManagers(), null);
                return this;
            } catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException
                    | KeyManagementException e) {
                throw new RuntimeException("Cannot create SSLContext", e);
            }
        }

        public HorreumClient build() throws IllegalStateException {

            if (sslContext == null) {
                try {
                    sslContext = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    // Do nothing
                }
            }

            ConfigService.KeycloakConfig keycloakConfig;
            try {
                URL url = new URL(this.horreumUrl + KEYCLOAK_BOOTSTRAP_URL);
                keycloakConfig = new ObjectMapper().readValue(url, ConfigService.KeycloakConfig.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ResteasyClientBuilderImpl clientBuilder = new ResteasyClientBuilderImpl();

            //Override default ObjectMapper Provider
            clientBuilder.register(new CustomResteasyJackson2Provider(), 100);
            clientBuilder.sslContext(sslContext);

            if (horreumApiKey != null) {
                clientBuilder.register(new HorreumApiKeyAuthentication(horreumApiKey));
            } else if (keycloakConfig.url == null || keycloakConfig.url.isEmpty()) {
                clientBuilder.register(new BasicAuthentication(horreumUser, horreumPassword));
            } else {
                // register Keycloak Request Filter
                clientBuilder.register(new KeycloakClientRequestFilter(
                        keycloakConfig.url,
                        keycloakConfig.realm,
                        horreumUser,
                        horreumPassword,
                        keycloakConfig.clientId,
                        sslContext));
            }

            // Other MessageBodyReaders/Writers that may not be found by ServiceLoader mechanism
            clientBuilder.register(new StringTextStar());
            clientBuilder.register(new DefaultTextPlain());

            ResteasyClient client = clientBuilder.build();
            ResteasyWebTarget target = client.target(horreumUrl);

            return new HorreumClient(client,
                    target.proxyBuilder(ActionService.class).build(),
                    target.proxyBuilder(AlertingService.class).build(),
                    target.proxyBuilder(BannerService.class).build(),
                    target.proxyBuilder(ChangesService.class).build(),
                    target.proxyBuilder(ConfigService.class).build(),
                    target.proxyBuilder(DatasetService.class).build(),
                    target.proxyBuilder(ExperimentService.class).build(),
                    target.proxyBuilder(NotificationService.class).build(),
                    target.proxyBuilder(ReportService.class).build(),
                    new RunServiceExtension(target, target.proxyBuilder(RunService.class).build()),
                    target.proxyBuilder(SchemaService.class).build(),
                    target.proxyBuilder(SqlService.class).build(),
                    target.proxyBuilder(SubscriptionService.class).build(),
                    target.proxyBuilder(TestService.class).build(),
                    target.proxyBuilder(UserService.class).build(),
                    target.proxyBuilder(UIService.class).build());
        }
    }

}
