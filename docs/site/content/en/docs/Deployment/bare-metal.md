---
title: Bare-metal
description: Install Horreum on a bare-metal machine
date: 2023-10-15
weight: 1
---


This guide [WILL](https://github.com/RedHatPerf/project-tracking/issues/393) document production installation without helper \*-compose scripts.

TODO: [THE CONTENT BELOW NEEDS TO BE UPDATED](https://github.com/RedHatPerf/project-tracking/issues/393)

## Setup database

You need PostgreSQL 12 (or later) installed; setup is out of scope of this guide. Create a new database for the application called `horreum` and limited-priviledge user `appuser` with a secure password:

```bash
export PGHOST=127.0.0.1
export PGPORT=5432
export PGUSER=dbadmin
export PGPASSWORD="Curr3ntAdm!nPwd"
psql -c "CREATE DATABASE horreum" postgres
export PGDATABASE=horreum
psql -c "CREATE ROLE \"appuser\" noinherit login password 'SecurEpaSSw0!2D';" postgres
```

Now you need to setup a Keycloak user and database:

```bash
psql -c "CREATE ROLE \"keycloakuser\" noinherit login password 'An0th3rPA55w0rD';"
psql -c "CREATE DATABASE keycloak WITH OWNER = 'keycloakuser';"
```

## Keycloak setup

For complete Keycloak setup please refer to [Keycloak Getting Started](https://www.keycloak.org/docs/latest/getting_started/index.html) - you can also use existing Keycloak instance.

Get the [realm definition](https://github.com/Hyperfoil/Horreum/blob/master/infra/keycloak-horreum.json) and import it:

```bash
REALM_CONFIG=$(mktemp horreum-docker-compose.XXXX.yaml)
curl https://raw.githubusercontent.com/Hyperfoil/Horreum/master/infra/keycloak-horreum.json \
    -s -o $REALM_CONFIG
./bin/standalone.sh \
    -Dkeycloak.profile.feature.upload_scripts=enabled \
    -Dkeycloak.migration.action=import \
    -Dkeycloak.migration.provider=singleFile \
    -Dkeycloak.migration.file=$REALM_CONFIG \
    -Dkeycloak.migration.strategy=IGNORE_EXISTING
```

When Keycloak starts you should access its admin console and adjust URLs for clients `horreum` and `horreum-ui`:

- Root URL (`rootUrl`)
- Valid Redirect URIs (`redirectUris`) - make sure to include the `/*` to match all subpaths
- Admin URL (`adminUrl`)
- Web Oridins (`webOrigins`)

After that create the role `__user_reader`, go to 'Role Mappings' tab, select `realm-management` in Client Roles and give this user the `view-users` role. Make sure that this user has the `offline_access` Realm Role as well.

Now you can create team roles, users and [assign them appropriatelly](user_management.html). For correct integration with Grafana please remember to set email for each user (this will be used purely to match Grafana identities).

You should also open `horreum` client, switch to 'Credentials' tab and record the Secret (UUID identifier).

## Starting Horreum

Horreum is a Quarkus application and is [configured](https://quarkus.io/guides/config#overriding-properties-at-runtime) using one of these:

- Java system properties when starting the application
- Exported environment variables
- Environment variables definition in `.env` file in the current working directory

You should set up these variables:

```bash
# --- DATABASE ---
# These two URLs should be identical
QUARKUS_DATASOURCE_MIGRATION_JDBC_URL=jdbc:postgresql://db.local:5432/horreum
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db.local:5432/horreum
# This is the regular user Horreum will use for DB access
QUARKUS_DATASOURCE_USERNAME=appuser
QUARKUS_DATASOURCE_PASSWORD=SecurEpaSSw0!2D
# This is the user that has full control over 'horreum' database
QUARKUS_DATASOURCE_MIGRATION_USERNAME=dbadmin
QUARKUS_DATASOURCE_MIGRATION_PASSWORD=Curr3ntAdm!nPwd
# Secret generated during database setup: run `SELECT * FROM dbsecret` as DB admin
HORREUM_DB_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxx

# --- KEYCLOAK ---
# This URL must be accessible from Horreum, but does not have to be exposed to the world
QUARKUS_OIDC_AUTH_SERVER_URL=https://keycloak.local/auth/realms/horreum
# You might need to set this property to the external Keycloak URL
QUARKUS_OIDC_TOKEN_ISSUER=https://keycloak.example.com
# Secret found in Keycloak console
QUARKUS_OIDC_CREDENTIALS_SECRET=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
# Make sure to include the /auth path. This URL must be externally accessible.
HORREUM_KEYCLOAK_URL=https://keycloak.example.com/auth
# Keycloak URL for internal access
HORREUM_KEYCLOAK_MP_REST_URL=https://keycloak.local

# --- Grafana ---
HORREUM_GRAFANA_ADMIN_PASSWORD=g12aPHana+Pwd
# External Grafana URL
HORREUM_GRAFANA_URL=https://grafana.example.com:3443
# Internal Grafana URL. This should be secured as ATM Horreum sends the credentials using Basic auth.
HORREUM_GRAFANA_MP_REST_URL=https://grafana.local:3443

# --- HTTP ---
# You might also want to set the IP the webserver is listening to
QUARKUS_HTTP_HOST=123.45.67.89
QUARKUS_HTTP_PORT=443
# Any production instance should be run over secured connections
QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE=/path/to/keystore.jks
QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_PASSWORD=keystore-password
QUARKUS_HTTP_INSECURE_REQUESTS=disabled
# If you share the certificate for Horreum and Keycloak/Grafana disable HTTP/2 to avoid connection coalescing
QUARKUS_HTTP_HTTP2=false
# URL for Horreum external access (advertised in permanent links etc.)
HORREUM_URL=https://horreum.example.com
# Internal URL for services that load data from Horreum (e.g. Grafana)
HORREUM_INTERNAL_URL=https://horreum.local:8443

# --- Mailserver ---
QUARKUS_MAILER_FROM=horreum@horreum.example.com
QUARKUS_MAILER_HOST=smtp.example.com
QUARKUS_MAILER_PORT=25
QUARKUS_MAILER_START_TLS=disabled
QUARKUS_MAILER_LOGIN=disabled

# --- Other ---
# By default webhook notifications that fail to verify TLS integrity fail; set this to ignore verification result.
# HORREUM_HOOK_TLS_INSECURE=true

# This is an offline token for the __user_reader user. You can obtain that with
# curl -s -X POST https://keycloak.example.com/auth/realms/horreum/protocol/openid-connect/token \
#      -H 'content-type: application/x-www-form-urlencoded' \
#      -d 'username=__user_reader&password='$PASSWORD'&grant_type=password&client_id=horreum-ui&scope=offline_access' \
#     | jq -r .refresh_token
HORREUM_KEYCLOAK_USER_READER_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

With all this in you can finally start Horreum as any other Java application (note: dependencies in `repo/target/lib/` must be present):

```bash
java -jar repo/target/repo-1.0.0-SNAPSHOT-runner.jar
```
