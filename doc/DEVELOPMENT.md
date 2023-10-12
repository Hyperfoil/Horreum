# Development

## Build

You will need Java 17 and Maven 3.9 then execute

```bash
mvn clean install
mvn quarkus:dev -pl 'horreum-backend'
```

## Example data

You can preload Horreum with some [example data](https://github.com/Hyperfoil/Horreum/blob/master/infra-legacy/example-configuration.sh) with

```bash
./infra-legacy/example-configuration.sh
```

once Horreum is running.

## Main configuration

The main configuration of Horreum is in the [application.properties](https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/application.properties) file.

The database bootstrap script is in the [changeLog.xml](https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/db/changeLog.xml)


## Access Keycloak

You can access the Keycloak instance by using the URL provided by the

```bash
curl http://localhost:8080/api/config/keycloak | jq -r .url
```

command.

The following users are defined

| Role | Name | Password | Realm |
| ---- | ---- | -------- | ----- |
| Admin | `admin` | `secret` | |
| User | `user` | `secret` | `horreum` |

## Using an existing backup

You can use an existing backup of the database (PostgreSQL 13+) by a command like

```bash
mvn  quarkus:dev -pl '!horreum-integration-tests' \
  -Dhorreum.dev-services.postgres.database-backup=/opt/databases/horreum-prod-db/ \
  -Dhorreum.db.secret='M3g45ecr5t!' \
  -Dhorreum.dev-services.keycloak.db-password='prod-password' \
  -Dhorreum.dev-services.keycloak.admin-password='ui-prod-password' \
  -Dquarkus.datasource.username=user \
  -Dquarkus.datasource.password='prod-password' \
  -Dquarkus.liquibase.migration.migrate-at-start=false
```
