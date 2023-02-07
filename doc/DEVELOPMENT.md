# Development

## Using existing data

You can use a backup - f.ex a [pgmoneta](https://pgmoneta.github.io/) one - from an existing setup, like

```bash
cd /tmp
scp <username>@<hostname>:/path/to/backup/horreum-timestamp.tar.zstd .
mkdir data
cd data
tar -axf ../horreum-timestamp.tar.zstd
cd ..
chown -R <username>:<groupname> data/
```

and then starting the container with

```bash
podman run --name postgres --rm -d --network=host -v "/tmp/data/:/var/lib/postgresql/data:rw,z" docker.io/postgres:13
```

You have to choose the PostgreSQL container image that aligns with your backup files.


You can start the rest of the stack using the

```bash
./infra/podman-compose.sh
```

script. But, you need to comment out the

* `postgres`
* `db-init`

steps in `infra/docker-compose.yml` first.

Depending on your setup you may have to change user names and passwords in the following files:

In `horreum-backend/src/main/resources/application.properties`

* `quarkus.datasource.jdbc.url`
* `quarkus.datasource.username`
* `quarkus.datasource.password`
* `quarkus.datasource.migration.jdbc.url`
* `quarkus.datasource.migration.username`
* `quarkus.datasource.migration.password`
* `quarkus.liquibase.migration.migrate-at-start=false`
* `horreum.db.secret`

, in `infra/Dockerfile.keycloak `

* `ENV KC_DB_USERNAME`
* `ENV KC_DB_PASSWORD`

and in `infra/app-init.sh`

* `POSTGRES_PORT`
* `KEYCLOAK_ADMIN_TOKEN`

Make sure that the `hostname` and `port` against the database are defined. Like `DB_PORT` in `infra/docker-compose.yml`.

## Build tooling set-up

```bash
mvn -N io.takari:maven:wrapper
```

This set's up your environment with the maven wrapper tool

##

Alternatively you can build Horreum image (with `dev` tag) and run it (assuming that you've started the docker-compose/podman-compose infrastructure):

```bash
# The base image contains tools like curl and jq and horreum.sh script
podman build -f src/main/docker/Dockerfile.jvm.base -t quay.io/hyperfoil/horreum-base:latest .
podman push quay.io/hyperfoil/horreum-base:latest
mvn package
podman run --rm --name horreum_app --env-file horreum-backend/.env --network=host quay.io/hyperfoil/horreum:dev
```

> :warning: _If npm install fails_: please try clearing the node module cache `npm cache clean`

## Running in dev mode over HTTPS

> TODO: Dev mode currently does not work over HTTPS (this is Quinoa issue)

By default, the local setup uses plain HTTP. If you need to test HTTPS, run the docker-compose/podman-compose as usual (in this setup the other containers won't be secured) and then run:

```bash
./enable-https.sh
```

This script will amend `.env` file with few extra variables and configure Keycloak to redirect to secured ports. Then you can run

```bash
HTTPS=true mvn quarkus:dev
```

as usual - the `HTTPS=true` will use secured connections on the live-reload proxy on port 3000.

When you want to revert back to plain HTTP, run `./disable-https.sh` and drop the `HTTPS=true` env var.
