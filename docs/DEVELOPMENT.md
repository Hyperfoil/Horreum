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

## OpenAPI

The [OpenAPI](https://www.openapis.org/) for [Horreum](https://github.com/Hyperfoil/Horreum/) will be located in

```bash
./horreum-api/target/generated/openapi.yaml
```

after the build.

The [website](https://horreum.hyperfoil.io/docs/reference/api-reference/) is hosting a copy of the OpenAPI reference.

## Main configuration

The main configuration of Horreum is in the [application.properties](https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/application.properties) file.

The database bootstrap script is in the [changeLog.xml](https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/db/changeLog.xml)

## Releases

Horreum is pre-1.0 (0.y.z) and therefore we cannot guarantee binary compatability between patch releases (0.y.z to 0.y.z+1). We plan to increase minor versions (y values) when breaking APIs but API breaking bug fixes might appear in patch releases. [semver clause](https://semver.org/#spec-item-4)

## Credentials

Horreum is running on [localhost:8080](http://localhost:8080)

| Role | Name | Password |
| ---- | ---- | -------- |
| User | `user` | `secret` |


## Messaging

By default [Horreum](https://github.com/Hyperfoil/Horreum/) uses [SmallRye Messaging](https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/3.3/index.html) and
for example [Apache Artemis](https://activemq.apache.org/components/artemis/) has the messaging platform.

In the case of [Apache Artemis](https://activemq.apache.org/components/artemis/) the key is to configure the setup to work against the 
[application.properties](https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/application.properties) file.

So, for example:

Create a user
```
artemis user add
```

to add the `horreum` user - default password is `secret`.

So in `artemis-roles.properties`
```
horreum = horreum
```

In `broker.xml`
```
      <security-settings>
         <security-setting match="#">
            <permission type="createNonDurableQueue" roles="amq, horreum"/>
            <permission type="deleteNonDurableQueue" roles="amq, horreum"/>
            <permission type="createDurableQueue" roles="amq, horreum"/>
            <permission type="deleteDurableQueue" roles="amq, horreum"/>
            <permission type="createAddress" roles="amq, horreum"/>
            <permission type="deleteAddress" roles="amq, horreum"/>
            <permission type="consume" roles="amq, horreum"/>
            <permission type="browse" roles="amq, horreum"/>
            <permission type="send" roles="amq, horreum"/>
            <permission type="manage" roles="amq, horreum"/>
         </security-setting>
      </security-settings>
```

and

```
      <addresses>
         <address name="DLQ">
            <anycast>
               <queue name="DLQ" />
            </anycast>
         </address>
         <address name="ExpiryQueue">
            <anycast>
               <queue name="ExpiryQueue" />
            </anycast>
         </address>
         <address name="dataset-event">
            <multicast>
               <queue name="horreum-broker.dataset-event"/>
            </multicast>
         </address>
         <address name="run-recalc">
            <multicast>
               <queue name="horreum-broker.run-recalc"/>
            </multicast>
         </address>
      </addresses>
```

Look at the [Apache Artemis documentation](https://activemq.apache.org/components/artemis/documentation/) for more information.

## Access Keycloak

You can access the Keycloak instance by using the URL provided by the

```bash
curl -k -s http://localhost:8080/api/config/keycloak | jq -r .url
```

command.

The following users are defined

| Role | Name | Password | Realm |
| ---- | ---- | -------- | ----- |
| Admin | `admin` | `secret` | |
| User | `user` | `secret` | `horreum` |

## Troubleshooting development infrastructure

1. Clean cached files and rebuild

```shell
$ mvn clean -p remove-node-cache
$ mvn clean install -DskipTests -DskipITs
```

## Local development with Podman

[Podman 4](https://podman.io/) can be used for the development mode of Horreum.

Install of the podman packages:

``` bash
dnf install -y podman podman-plugins podman-docker
```

In one terminal do
``` bash
podman system service -t 0
```
And then configure `DOCKER_HOST` environment variable to resolve to the podman socket

``` bash
export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
```

and use the standard build commands.

## Using an existing backup

You can use an existing backup of the database (PostgreSQL 13+) by a command like

```bash
mvn  quarkus:dev -pl '!horreum-integration-tests' \
  -Dhorreum.dev-services.postgres.database-backup=/opt/databases/horreum-prod-db/ \
  -Dhorreum.dev-services.keycloak.db-password='prod-password' \
  -Dhorreum.dev-services.keycloak.admin-password='ui-prod-password' \
  -Dquarkus.datasource.username=user \
  -Dquarkus.datasource.password='prod-password' \
  -Dquarkus.liquibase.migration.migrate-at-start=false
```

or by placing a `horreum-backend/.env` file with content like

```
horreum.dev-services.postgres.database-backup=<path/to/db>
horreum.dev-services.keycloak.image=quay.io/keycloak/keycloak:20.0.1
horreum.dev-services.keycloak.db-username=<keycloak-user-name>
horreum.dev-services.keycloak.db-password=<keycloak-user-password>

horreum.dev-services.keycloak.admin-username=<keycloak-admin-name>
horreum.dev-services.keycloak.admin-password=<keycloak-admin-password>

quarkus.datasource.username=<horreum-user-name>
quarkus.datasource.password=<horreum-user-password>

# Set to `true` to migrate database schema at startup
quarkus.liquibase.migration.migrate-at-start=true
quarkus.liquibase.migration.validate-on-migrate=false

# Need user account with access to public schema
quarkus.datasource.migration.username=<migration-user-name>
quarkus.datasource.migration.password=<migration-user-password>
```

# Backports
When developing new features, we always create pull requests (PRs) in the `master` branch. However, we always support the latest stable branch. If you encounter an issue that requires a fix for the stable branch, you can add the `backport` (or `backport-squash`) label to the original PR and a new PR will be automatically generated. You will then need to review and merge the backport PR.

Which label should I use?
* `backport`: (default) this uses the `no-squash=true` option so that the tool tries to backport all commits coming from the original pull request you are trying to backport.
> _**Note**_ that in this case the commit SHAs should exist during the backporting, i.e,
delete the source branch only after the backporting PR got created.
* `backport-squash`: with this label you set `no-squash=false` option, so that the tool tries to backport the pull request `merge_commit_sha`.
> _**Note**_ the value of the `merge_commit_sha` attribute changes depending on the state of the pull request, see [Github doc](https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#get-a-pull-request) for more details.
