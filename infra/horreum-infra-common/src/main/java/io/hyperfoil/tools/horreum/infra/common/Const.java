package io.hyperfoil.tools.horreum.infra.common;

public class Const {
    public static final String HORREUM_DEV_POSTGRES_ENABLED = "horreum.dev-services.postgres.enabled";
    public static final String HORREUM_DEV_POSTGRES_BACKUP = "horreum.dev-services.postgres.database-backup";
    public static final String HORREUM_DEV_POSTGRES_IMAGE = "horreum.dev-services.postgres.image";
    public static final String HORREUM_DEV_POSTGRES_NETWORK_ALIAS = "horreum.dev-services.postgres.network-alias";
    public static final String HORREUM_DEV_POSTGRES_SSL_CERTIFICATE = "horreum.dev-services.postgres.ssl-certificate";
    public static final String HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY = "horreum.dev-services.postgres.ssl-certificate-key";

    public static final String HORREUM_DEV_KEYCLOAK_ENABLED = "horreum.dev-services.keycloak.enabled";
    public static final String HORREUM_DEV_KEYCLOAK_IMAGE = "horreum.dev-services.keycloak.image";
    public static final String HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS = "horreum.dev-services.keycloak.network-alias";

    public static final String HORREUM_DEV_KEYCLOAK_CONTAINER_PORT = "horreum.dev-services.keycloak.container-port";
    public static final String HORREUM_DEV_KEYCLOAK_DB_USERNAME = "horreum.dev-services.keycloak.db-username";
    public static final String HORREUM_DEV_KEYCLOAK_DB_PASSWORD = "horreum.dev-services.keycloak.db-password";
    public static final String HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE = "horreum.dev-services.keycloak.https-certificate";
    public static final String HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE_KEY = "horreum.dev-services.keycloak.https-certificate-key";

    public static final String HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME = "horreum.dev-services.keycloak.admin-username";
    public static final String HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD = "horreum.dev-services.keycloak.admin-password";

    public static final String HORREUM_DEV_DB_USERNAME = "horreum.db.username";
    public static final String HORREUM_DEV_DB_PASSWORD = "horreum.db.password";
    public static final String HORREUM_DEV_DB_DATABASE = "horreum.db.database";

    public static final String HORREUM_DEV_HORREUM_HORREUM_IMAGE = "horreum.dev-services.horreum.horreum.image";
    public static final String HORREUM_DEV_HORREUM_NETWORK_ALIAS = "horreum.dev-services.horreum.network-alias";
    public static final String HORREUM_DEV_HORREUM_CONTAINER_PORT = "horreum.dev-services.horreum.container-port";

    public static final String HORREUM_DEV_AMQP_ENABLED = "horreum.dev-services.amqp.enabled";
    public static final String HORREUM_DEV_AMQP_IMAGE = "horreum.dev-services.amqp.image";
    public static final String HORREUM_DEV_AMQP_NETWORK_ALIAS = "horreum.dev-services.amqp.network-alias";

    public static final String DEFAULT_DB_USERNAME = "dbadmin";
    public static final String DEFAULT_DB_PASSWORD = "secret";
    public static final String DEFAULT_DBDATABASE = "horreum";

    public static final String DEFAULT_KC_DB_USERNAME = "keycloak";
    public static final String DEFAULT_KC_DB_PASSWORD = "secret";

    public static final String DEFAULT_KC_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_KC_ADMIN_PASSWORD = "secret";

    public static final String DEFAULT_POSTGRES_NETWORK_ALIAS = "horreum-dev-postgres";

    public static final String DEFAULT_KEYCLOAK_NETWORK_ALIAS = "horreum-dev-keycloak";
    public static final String DEFAULT_AMQP_NETWORK_ALIAS = "horreum-dev-amqp";
    public static final String DEFAULT_HORREUM_NETWORK_ALIAS = "horreum-dev-horreum";
    public static final String DEFAULT_AMQP_USERNAME = "horreum";
    public static final String DEFAULT_AMQP_PASSWORD = "secret";
}
