# Development

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
