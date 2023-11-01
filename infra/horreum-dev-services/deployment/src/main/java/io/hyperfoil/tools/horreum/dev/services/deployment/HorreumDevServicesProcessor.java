package io.hyperfoil.tools.horreum.dev.services.deployment;

import io.hyperfoil.tools.horreum.dev.services.deployment.config.DevServicesConfig;
import io.hyperfoil.tools.horreum.dev.services.runtime.dev.HorreumDevJsonRpcService;
import io.hyperfoil.tools.horreum.infra.common.HorreumResources;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = {HorreumDevServicesProcessor.IsEnabled.class, GlobalDevServicesConfig.Enabled.class})
public class HorreumDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(HorreumDevServicesProcessor.class);

    private static volatile DevServicesResultBuildItem.RunningDevService horreumKeycloakDevService;
    private static volatile DevServicesResultBuildItem.RunningDevService horreumPostgresDevService;

    @BuildStep(onlyIf = {IsDevelopment.class})
    public void startHorreumContainers(
            BuildProducer<DevServicesResultBuildItem> devServicesResultBuildItemBuildProducer,
            DockerStatusBuildItem dockerStatusBuildItem,
            BuildProducer<HorreumDevServicesConfigBuildItem> horreumBuildItemBuildProducer,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            DevServicesConfig horreumBuildTimeConfig,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LaunchModeBuildItem launchMode,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig
    ) {


        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Horreum Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);

        boolean errors = false;

        LOG.infof("Horreum dev services (enabled: ".concat(Boolean.toString(horreumBuildTimeConfig.enabled)).concat(")"));

        if (horreumBuildTimeConfig.enabled) {
            try {

                if (errors = !dockerStatusBuildItem.isDockerAvailable()) {
                    LOG.warn("Docker dev service instance not found");
                }

                if (!errors) {

                    //TODO:: check to see if devServicesConfiguration has changed
                    if (horreumKeycloakDevService == null || horreumPostgresDevService == null) {

                        LOG.infof("Starting Horreum containers");

                        final Map<String, String> containerArgs = new HashMap<>();
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_ENABLED, Boolean.toString(horreumBuildTimeConfig.keycloak.enabled));
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_IMAGE, horreumBuildTimeConfig.keycloak.image);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, horreumBuildTimeConfig.keycloak.networkAlias);
                        containerArgs.put(HORREUM_DEV_POSTGRES_ENABLED, Boolean.toString(horreumBuildTimeConfig.postgres.enabled));
                        containerArgs.put(HORREUM_DEV_POSTGRES_IMAGE, horreumBuildTimeConfig.postgres.image);
                        containerArgs.put(HORREUM_DEV_POSTGRES_NETWORK_ALIAS, horreumBuildTimeConfig.postgres.networkAlias);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_DB_USERNAME, horreumBuildTimeConfig.keycloak.dbUsername);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_DB_PASSWORD, horreumBuildTimeConfig.keycloak.dbPassword);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME, horreumBuildTimeConfig.keycloak.adminUsername);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD, horreumBuildTimeConfig.keycloak.adminPassword);

                        horreumBuildTimeConfig.keycloak.containerPort.ifPresent(keycloakPort -> containerArgs.put(HORREUM_DEV_KEYCLOAK_CONTAINER_PORT, keycloakPort));
                        horreumBuildTimeConfig.postgres.databaseBackup.map(File::getAbsolutePath).ifPresent(backupFilename -> containerArgs.put(HORREUM_DEV_POSTGRES_BACKUP, backupFilename));

                        if (horreumBuildTimeConfig.keycloak.httpsEnabled) {
                            containerArgs.putAll(selfSignedCertificateContainerArgs());
                        }
                        Map<String, String> envvars = HorreumResources.startContainers(Collections.unmodifiableMap(containerArgs));

                        Map<String, String> postrgesConfig = new HashMap<>();
                        String jdbcUrl = HorreumResources.postgreSQLResource.getJdbcUrl();

                        postrgesConfig.put("quarkus.datasource.jdbc.url", jdbcUrl);
                        postrgesConfig.put("quarkus.datasource.migration.jdbc.url", jdbcUrl);

                        horreumPostgresDevService = new DevServicesResultBuildItem.RunningDevService(
                                HorreumResources.postgreSQLResource.getContainer().getContainerName(),
                                HorreumResources.postgreSQLResource.getContainer().getContainerId(),
                                HorreumResources.postgreSQLResource.getContainer()::close,
                                postrgesConfig);

                        Map<String, String> keycloakConfig = new HashMap<>();
                        Integer keycloakPort = HorreumResources.keycloakResource.getContainer().getMappedPort(horreumBuildTimeConfig.keycloak.httpsEnabled ? 8443 : 8080);
                        String keycloakURL = (horreumBuildTimeConfig.keycloak.httpsEnabled ? "https" : "http") + "://localhost:" + keycloakPort;

                        keycloakConfig.put("horreum.keycloak.url", keycloakURL);
                        keycloakConfig.put("quarkus.oidc.auth-server-url", keycloakURL + "/realms/horreum");
                        keycloakConfig.put("quarkus.oidc.credentials.secret", envvars.get("quarkus.oidc.credentials.secret"));
                        if (envvars.containsKey("quarkus.oidc.tls.trust-store-file")) {
                            keycloakConfig.put("quarkus.oidc.tls.trust-store-file", envvars.get("quarkus.oidc.tls.trust-store-file"));
                            keycloakConfig.put("quarkus.oidc.tls.verification", "certificate-validation"); // could be "none" and disable TLS verification altogether
                        }

                        horreumKeycloakDevService = new DevServicesResultBuildItem.RunningDevService(
                                HorreumResources.keycloakResource.getContainer().getContainerName(),
                                HorreumResources.keycloakResource.getContainer().getContainerId(),
                                HorreumResources.keycloakResource.getContainer()::close,
                                keycloakConfig);
                    }
                }

                if (horreumKeycloakDevService == null || horreumPostgresDevService == null) {
                    if (!errors) {
                        compressor.close();
                    } else {
                        compressor.closeAndDumpCaptured();
                    }
                    return;
                }

                Runnable closeTask = () -> {
                    if (horreumKeycloakDevService != null) {
                        try {
                            horreumKeycloakDevService.close();
                        } catch (Throwable t) {
                            LOG.error("Failed to stop Keycloak container", t);
                        }
                    }
                    if (horreumPostgresDevService != null) {
                        try {
                            horreumPostgresDevService.close();
                        } catch (Throwable t) {
                            LOG.error("Failed to stop Postgres container", t);
                        }
                    }
                    horreumKeycloakDevService = null;
                    horreumPostgresDevService = null;
                };
                closeBuildItem.addCloseTask(closeTask, true);

                if ((horreumKeycloakDevService != null || horreumPostgresDevService != null) && !errors) {
                    compressor.close();
                } else {
                    compressor.closeAndDumpCaptured();
                }
            } catch (Throwable t) {
                compressor.closeAndDumpCaptured();
                throw new RuntimeException(t);
            }

            devServicesResultBuildItemBuildProducer.produce(horreumKeycloakDevService.toBuildItem());
            devServicesResultBuildItemBuildProducer.produce(horreumPostgresDevService.toBuildItem());
        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    public CardPageBuildItem pages() {

        CardPageBuildItem card = new CardPageBuildItem();
        card.addPage(Page.webComponentPageBuilder()
                .title("Sample Data")
                .componentLink("horreum-sample-data.js")
                .icon("font-awesome-solid:table")
                .dynamicLabelJsonRPCMethodName("getIsSampleDataLoaded"));

        return card;
    }


    @BuildStep
    JsonRPCProvidersBuildItem createJsonRPCService() {
        return new JsonRPCProvidersBuildItem("horreum", HorreumDevJsonRpcService.class);
    }

    @BuildStep
    AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem
                .builder()
                .addBeanClass(HorreumDevJsonRpcService.class)
                .setUnremovable()
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .build();
    }


    public static class IsEnabled implements BooleanSupplier {
        DevServicesConfig config;

        public boolean getAsBoolean() {
            return config.enabled;
        }
    }

    // generate self-signed certificate and return it as args to be processed by KeycloakResource
    private Map<String, String> selfSignedCertificateContainerArgs() throws Throwable {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        X509Certificate cert = SelfSignedCertGenerator.generateSelfSignedCert(keyPair, "SHA256withRSA", "testcontainer", 123);

        StringWriter certWriter = new StringWriter(), keyWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(certWriter)) {
            pemWriter.writeObject(cert);
        }
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(keyWriter)) {
            pemWriter.writeObject(keyPair.getPrivate());
        }

        return Map.of(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE, certWriter.toString(), HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE_KEY, keyWriter.toString());
    }
    
    /**
     * Generates a self-signed certificate using the BouncyCastle lib.
     */
    private static final class SelfSignedCertGenerator {

        private SelfSignedCertGenerator() {}

        public static X509Certificate generateSelfSignedCert(KeyPair keyPair, String hashAlgorithm, String cn, int days) throws OperatorCreationException, CertificateException, CertIOException {
            Instant now = Instant.now();
            X500Name x500Name = new X500Name("CN=" + cn);

            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    x500Name,
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now),
                    Date.from(now.plus(Duration.ofDays(days))),
                    x500Name,
                    keyPair.getPublic()
            ).addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()))
             .addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()))
             .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(certificateBuilder.build(new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate())));
        }

        /**
         * Creates the hash value of the public key.
         */
        private static SubjectKeyIdentifier createSubjectKeyId(PublicKey publicKey) throws OperatorCreationException {
            return new X509ExtensionUtils(new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)))
                    .createSubjectKeyIdentifier(SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
        }

        /**
         * Creates the hash value of the authority public key.
         */
        private static AuthorityKeyIdentifier createAuthorityKeyId(PublicKey publicKey) throws OperatorCreationException {
            return new X509ExtensionUtils(new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)))
                    .createAuthorityKeyIdentifier(SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
        }
    }
}
