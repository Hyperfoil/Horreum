#!/bin/bash

fail() {
  echo $1 1>&2
  exit 1
}

download() {
  curl -s --fail $BASE_URL/infra/$1 -o $WORKDIR/infra/$1 || fail "Cannot download $1"
}

require() {
  if ! command -v $1 > /dev/null; then
    fail "Missing '"$1"' command; please install it."
  fi
}

require curl
require jq
if [ -n "$CONTAINER" ]; then
  echo "Use of $CONTAINER forced"
elif command -v podman > /dev/null && command -v podman-compose > /dev/null; then
  CONTAINER=podman
elif command -v docker > /dev/null && command -v docker-compose > /dev/null; then
  CONTAINER=docker
else
  fail "Neither podman nor docker (with -compose) was found, please make sure these are installed"
fi
echo "Using $CONTAINER to run containers..."

WORKDIR=$(mktemp -t -d horreum.XXXX)
echo "Downloading files to $WORKDIR..."
mkdir -p $WORKDIR/infra
VERSION=${VERSION:-0.6}
IMGTAG=${IMGTAG:-0.6}
BASE_URL=https://raw.githubusercontent.com/Hyperfoil/Horreum/${VERSION}
for FILE in \
  app-init.sh \
  create-db.sh \
  docker-compose.yml \
  Dockerfile.keycloak \
  grafana.env \
  grafana.sh \
  keycloak.sh \
  keycloak-horreum.json \
  podman-compose.sh \
  postgres.env
do
  download $FILE && [ "${FILE: -3}" == ".sh" ] && chmod a+x $WORKDIR/infra/$FILE
done

mkdir -p $WORKDIR/horreum-backend
echo "Starting infrastructure containers..."
if [ "$CONTAINER" = "podman" ]; then
  $WORKDIR/infra/podman-compose.sh > /dev/null 2>&1
else
  docker-compose -f $WORKDIR/infra/docker-compose.yml -p horreum up -d
fi

until $CONTAINER inspect horreum_app-init_1 | jq -e '.[0].State.Status == "exited"' > /dev/null; do
  echo "Waiting for app-init container to complete..."
  sleep 2
done;
$CONTAINER run -d --net host --name horreum_app --label com.docker.compose.project=horreum \
  --env-file $WORKDIR/horreum-backend/.env quay.io/hyperfoil/horreum:$IMGTAG

until curl --fail -s http://localhost:8080 > /dev/null; do
  if $CONTAINER inspect horreum_app | jq -e '.[0].State.Status == "exited"' > /dev/null; then
    fail "Horreum failed to start. Please run '$CONTAINER logs horreum_app' to see the cause."
  fi
  echo "Waiting for Horreum to boot..."
  sleep 1
done
if command -v xdg-open > /dev/null; then
  xdg-open http://localhost:8080
fi
echo -e "\nHorreum started on http://localhost:8080"
