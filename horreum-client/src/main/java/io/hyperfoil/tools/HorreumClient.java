package io.hyperfoil.tools;

import io.hyperfoil.tools.auth.KeycloakClientRequestFilter;
import io.hyperfoil.tools.horreum.api.services.ActionService;
import io.hyperfoil.tools.horreum.api.services.AlertingService;
import io.hyperfoil.tools.horreum.api.services.BannerService;
import io.hyperfoil.tools.horreum.api.services.ChangesService;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.NotificationService;
import io.hyperfoil.tools.horreum.api.services.ReportService;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.SqlService;
import io.hyperfoil.tools.horreum.api.services.SubscriptionService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.api.services.UserService;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.StringTextStar;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.UriBuilder;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

public class HorreumClient implements Closeable {
    private final ResteasyClient client;
    public final ActionService actionService;
    public final AlertingService alertingService;
    public final BannerService bannerService;
    public final DatasetService datasetService;
    public final ChangesService changesService;
    public final NotificationService notificationService;
    public final ReportService reportService;
    public final RunServiceExtension runService;
    public final SchemaService schemaService;
    public final SqlService sqlService;
    public final SubscriptionService subscriptionService;
    public final TestService testService;
    public final UserService userService;

    private HorreumClient(ResteasyClient client,
                          ActionService actionService, AlertingService alertingService, BannerService bannerService, DatasetService datasetService, ChangesService changesService,
                          NotificationService notificationService, ReportService reportService, RunServiceExtension runServiceExtension, SchemaService schemaService, SqlService sqlService,
                          SubscriptionService subscriptionService, TestService horreumTestService, UserService userService) {
        this.client = client;
        this.alertingService = alertingService;
        this.bannerService = bannerService;
        this.datasetService = datasetService;
        this.changesService = changesService;
        this.actionService = actionService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.runService = runServiceExtension;
        this.schemaService = schemaService;
        this.sqlService = sqlService;
        this.subscriptionService = subscriptionService;
        this.testService = horreumTestService;
        this.userService = userService;
    }

    @Override
    public void close() {
        client.close();
    }

    public static class Builder {
        private String horreumUrl;
        private String keycloakUrl;
        private String keycloakRealm = "horreum";
        private String horreumUser;
        private String horreumPassword;
        private String clientId = "horreum-ui";
        private String clientSecret;
        private SSLContext sslContext;

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
            } catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException | KeyManagementException e) {
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

            KeycloakClientRequestFilter requestFilter = new KeycloakClientRequestFilter(keycloakUrl,
                    keycloakRealm,
                    horreumUser,
                    horreumPassword,
                    clientId,
                    clientSecret,
                    sslContext);

            ResteasyClientBuilderImpl clientBuilder = new ResteasyClientBuilderImpl();

            //Override default ObjectMapper Provider
            clientBuilder.register(new CustomResteasyJackson2Provider(), 100);
            clientBuilder.sslContext(sslContext);

            //Register Keycloak Request Filter
            clientBuilder.register(requestFilter);
            // Other MessageBodyReaders/Writers that may not be found by ServiceLoader mechanism
            clientBuilder.register(new StringTextStar());
            clientBuilder.register(new DefaultTextPlain());

            ResteasyClient client = clientBuilder.build();
            ResteasyWebTarget target = client.target(UriBuilder.fromPath(this.horreumUrl));

            return new HorreumClient(client,
                  target.proxyBuilder(ActionService.class).build(),
                  target.proxyBuilder(AlertingService.class).build(),
                  target.proxyBuilder(BannerService.class).build(),
                  target.proxyBuilder(DatasetService.class).build(),
                  target.proxyBuilder(ChangesService.class).build(),
                  target.proxyBuilder(NotificationService.class).build(),
                  target.proxyBuilder(ReportService.class).build(),
                  new RunServiceExtension(target, target.proxyBuilder(RunService.class).build()),
                  target.proxyBuilder(SchemaService.class).build(),
                  target.proxyBuilder(SqlService.class).build(),
                  target.proxyBuilder(SubscriptionService.class).build(),
                  target.proxyBuilder(TestService.class).build(),
                  target.proxyBuilder(UserService.class).build());
        }
    }

}
