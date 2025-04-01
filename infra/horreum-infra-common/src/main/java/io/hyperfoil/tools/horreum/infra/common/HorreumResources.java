package io.hyperfoil.tools.horreum.infra.common;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.ws.rs.client.ClientBuilder;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.utility.LogUtils;

import io.hyperfoil.tools.horreum.infra.common.resources.KeycloakResource;
import io.hyperfoil.tools.horreum.infra.common.resources.PostgresResource;

public class HorreumResources {

    private static Keycloak keycloak;
    private static final String HORREUM_REALM = System.getProperty("horreum.realm", "horreum");
    private static final String KEYCLOAK_REALM = System.getProperty("keycloak.realm", "master");

    public static Properties configProperties;

    public static final String HORREUM_USERNAME;
    public static final String HORREUM_PASSWORD;

    private static final Integer ContainerStartTimeout = 60;
    private static final TimeUnit ContainerStartTimeoutUnit = TimeUnit.SECONDS;
    private static final Integer ContainerStartRetries = 1;

    private static final Logger log = Logger.getLogger(HorreumResources.class);

    static {
        configProperties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream propertyStream = classLoader.getResourceAsStream("env.properties");
                InputStream applicationStream = classLoader.getResourceAsStream("application.properties")) {
            if (propertyStream != null) {
                configProperties.load(propertyStream);
            } else {
                throw new RuntimeException("Could not load test configuration");
            }
            if (applicationStream != null) {
                Properties horreumProps = new Properties();
                horreumProps.load(applicationStream);
                configProperties.putAll(horreumProps);
            } else {
                throw new RuntimeException("Could not load application properties");
            }

            HORREUM_USERNAME = getProperty("horreum.username");
            HORREUM_PASSWORD = getProperty("horreum.password");

        } catch (IOException ioException) {
            throw new RuntimeException("Failed to load configuration properties");
        }
    }

    private static final Network network = Network.newNetwork();
    public static PostgresResource postgreSQLResource = new PostgresResource();
    public static KeycloakResource keycloakResource = new KeycloakResource();

    protected static String getProperty(String propertyName) {
        String override = System.getProperty(propertyName);
        if (override != null) {
            override = override.trim();
            if (!override.isEmpty()) {
                return override;
            }
        }
        String value = configProperties.getProperty(propertyName);
        if (value == null) {
            throw new IllegalStateException("Missing property value for " + propertyName);
        }
        return value.trim();
    }

    public static Map<String, String> startContainers(Map<String, String> initArgs) {
        Map<String, String> envVariables = new HashMap<>(initArgs);
        envVariables.put("inContainer", "true");
        envVariables.put("STOP_SIGNAL", "SIGKILL");

        Optional<Network> optionalNetwork = Optional.of(network);

        if (Boolean.parseBoolean(initArgs.get(HORREUM_DEV_POSTGRES_ENABLED))) {

            String QUARKUS_DATASOURCE_PASSWORD = getProperty("quarkus.datasource.password");
            envVariables.put("QUARKUS_DATASOURCE_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);
            envVariables.put("QUARKUS_DATASOURCE_MIGRATION_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);

            envVariables.put(HORREUM_DEV_DB_DATABASE, DEFAULT_DBDATABASE);
            envVariables.put(HORREUM_DEV_DB_USERNAME, DEFAULT_DB_USERNAME);
            envVariables.put(HORREUM_DEV_DB_PASSWORD, DEFAULT_DB_PASSWORD);

            postgreSQLResource.init(envVariables);

            Map<String, String> postgresEnv = postgreSQLResource.start(optionalNetwork);

            waitForContainerReady(postgreSQLResource.getContainer(), " database system is ready to accept connections");

            envVariables.putAll(postgresEnv);
            envVariables.putAll(postgresCertificateProperties(initArgs));
        }
        if (Boolean.parseBoolean(initArgs.get(HORREUM_DEV_KEYCLOAK_ENABLED))) {
            keycloakResource.init(envVariables);
            Map<String, String> keycloakEnv = keycloakResource.start(optionalNetwork);

            waitForContainerReady(keycloakResource.getContainer(), "started in");

            envVariables.put("keycloak.host", keycloakEnv.get("keycloak.host"));
            envVariables.put("horreum.keycloak.url", keycloakEnv.get("keycloak.host"));
            envVariables.put("quarkus.oidc.auth-server-url", keycloakEnv.get("keycloak.host") + "/realms/" + HORREUM_REALM);
            envVariables.putAll(oidcTruststoreProperties(initArgs));

            keycloak = KeycloakBuilder.builder()
                    .serverUrl(keycloakEnv.get("keycloak.host"))
                    .realm(KEYCLOAK_REALM)
                    .username(initArgs.get(HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME))
                    .password(initArgs.get(HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD))
                    .clientId("admin-cli")
                    .resteasyClient(((ResteasyClientBuilder) ClientBuilder.newBuilder()).disableTrustManager().build())
                    .build();

            //update running keycloak realm with dev services configuration
            try {
                String httpPort = initArgs.get("quarkus.http.port");
                String httpHost = initArgs.get("quarkus.http.host");

                ClientRepresentation uiClient = keycloak.realm(HORREUM_REALM).clients().findByClientId("horreum-ui").get(0);
                uiClient.getWebOrigins().add("http://" + httpHost + ":" + httpPort);
                uiClient.getRedirectUris().add("http://" + httpHost + ":" + httpPort + "/*");
                keycloak.realm(HORREUM_REALM).clients().get(uiClient.getId()).update(uiClient);

                ClientRepresentation mainClient = keycloak.realm(HORREUM_REALM).clients().findByClientId("horreum").get(0);
                envVariables.put("quarkus.oidc.credentials.secret",
                        keycloak.realm(HORREUM_REALM).clients().get(mainClient.getId()).getSecret().getValue());
            } catch (Exception e) {
                String msg = "Unable to re-configure keycloak instance: " + e.getLocalizedMessage();
                log.error(msg);
                throw new RuntimeException(msg);
            }
        }

        log.info("Waiting for test infrastructure to start");

        return envVariables;
    }

    public static void waitForContainerReady(GenericContainer<?> container, String pattern) {
        if (container != null) {
            WaitingConsumer waitingConsumer = new WaitingConsumer();

            LogUtils.followOutput(DockerClientFactory.instance().client(), container.getContainerId(), waitingConsumer);
            try {
                waitingConsumer.waitUntil(
                        outputFrame -> outputFrame.getUtf8String().contains(pattern), ContainerStartTimeout,
                        ContainerStartTimeoutUnit, ContainerStartRetries);
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out waiting for " + container.getContainerName() + " container to start");
            }
        } else {
            throw new RuntimeException("No container!");
        }
    }

    // --- //

    private static Map<String, String> postgresCertificateProperties(Map<String, String> initArgs) {
        if (initArgs.containsKey(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE)) {
            try {
                File certFile = File.createTempFile("horreum-dev-postgres-", ".crt");
                certFile.deleteOnExit();
                try (OutputStream outputStream = new FileOutputStream(certFile)) {
                    outputStream.write(initArgs.get(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE).getBytes(StandardCharsets.UTF_8));
                }
                return Map.of("quarkus.datasource.jdbc.sslrootcert", certFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Could not write postgres certificate file", e);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> oidcTruststoreProperties(Map<String, String> initArgs) {
        if (initArgs.containsKey(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE)
                && initArgs.containsKey(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE_KEY)) {
            return Map.of("quarkus.oidc.tls.trust-store-file", createOidcPKCS12Store(initArgs));
        } else {
            return Collections.emptyMap();
        }
    }

    // create a pkcs12 trust store file (for OIDC and Keycloak admin client) from the certificate and private key
    private static String createOidcPKCS12Store(Map<String, String> initArgs) {
        try {
            File keycloakTrustStore = File.createTempFile("horreum-dev-keycloak-", ".pkcs12");
            keycloakTrustStore.deleteOnExit();

            X509CertificateHolder cert = (X509CertificateHolder) new PEMParser(
                    new StringReader(initArgs.get(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE))).readObject();
            PEMKeyPair keyPair = (PEMKeyPair) new PEMParser(
                    new StringReader(initArgs.get(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE_KEY))).readObject();

            KeyStore pkcsStore = KeyStore.getInstance("PKCS12", new BouncyCastleProvider());
            pkcsStore.load(null);
            pkcsStore.setKeyEntry("", new JcaPEMKeyConverter().getPrivateKey(keyPair.getPrivateKeyInfo()), null,
                    new X509Certificate[] { new JcaX509CertificateConverter().getCertificate(cert) });

            try (OutputStream outputStream = new FileOutputStream(keycloakTrustStore)) {
                pkcsStore.store(outputStream, "password".toCharArray()); // "password" is the default in quarkus OIDC (OidcCommonUtils#setHttpClientOptions)
            }

            return keycloakTrustStore.getAbsolutePath();
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Could not write Keycloak HTTPS certificate", e);
        }
    }

    public static void stopContainers() {
        postgreSQLResource.stop();
        keycloakResource.stop();
    }

    public static Network getNetwork() {
        return network;
    }

}
