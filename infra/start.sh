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
  if [ "$CONTAINER" = "podman" ]; then
    LABEL="io.podman.compose.project"
  else
    LABEL="com.docker.compose.project"
  fi
elif command -v podman > /dev/null && command -v podman-compose > /dev/null; then
  CONTAINER=podman
  LABEL="io.podman.compose.project"
elif command -v docker > /dev/null && command -v docker-compose > /dev/null; then
  CONTAINER=docker
  LABEL="com.docker.compose.project"
else
  fail "Neither podman nor docker (with -compose) was found, please make sure these are installed"
fi
echo "Using $CONTAINER to run containers..."

REQUIRED_PORTS=" 8080 8180 4040 5432 "
set -o pipefail
for hexport in $(cat /proc/net/tcp /proc/net/tcp6 | grep ' 0A ' | cut -f 3 -d : | cut -f 1 -d ' '); do
  USED_PORT=$((16#$hexport))
  if echo "$REQUIRED_PORTS" | grep -F " $USED_PORT " > /dev/null; then
     fail "Port $USED_PORT is in use; please stop any service running there before starting Horreum"
  fi
done

WORKDIR=$(mktemp -t -d horreum.XXXX)
echo "Downloading files to $WORKDIR..."
mkdir -p $WORKDIR/infra
VERSION=${VERSION:-0.7}
IMGTAG=${IMGTAG:-0.7}
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
$CONTAINER run -d --net host --name horreum_app \
  --label $LABEL=horreum \
  --env-file $WORKDIR/horreum-backend/.env \
  quay.io/hyperfoil/horreum:$IMGTAG

until curl --fail -s http://localhost:8080/api/config/version > $WORKDIR/version; do
  if $CONTAINER inspect horreum_app | jq -e '.[0].State.Status == "exited"' > /dev/null; then
    fail "Horreum failed to start. Please run '$CONTAINER logs horreum_app' to see the cause."
  fi
  echo "Waiting for Horreum to boot..."
  sleep 1
done
VERSION=$(jq -r .version $WORKDIR/version)
COMMIT_ID=$(jq -r .commit $WORKDIR/version)
if command -v xdg-open > /dev/null; then
  xdg-open http://localhost:8080
fi
echo -e "\nHorreum $VERSION (commit $COMMIT_ID) started on http://localhost:8080"
echo "Please run this when you're finished:"
echo "podman kill \$(podman ps -q --filter 'label=io.podman.compose.project=horreum')"
echo "podman rm \$(podman ps -q -a --filter 'label=io.podman.compose.project=horreum')"
echo "rm -r $WORKDIR"