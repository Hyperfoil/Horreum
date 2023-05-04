#!/bin/bash

# This is a workaround for https://github.com/containers/podman-compose/issues/397

# Remove old containers
RUNNING_CONTAINERS=$(podman ps -a --format json | jq -r '.[] | select((.Labels."com.docker.compose.project" == "horreum") and (.State == "running")).Id')
if [ -n "$RUNNING_CONTAINERS" ]; then
    podman kill $RUNNING_CONTAINERS
fi
CONTAINERS=$(podman ps -a --format json | jq -r '.[] | select(.Labels."com.docker.compose.project" == "horreum").Id')
if [ -n "$CONTAINERS" ]; then
    podman rm $CONTAINERS
fi

ORIGINAL_DIR=$(pwd)
cd $(dirname $0)

# Delete leftovers from Docker runs: files owned by root
rm -rf ../horreum-backend/.env ../.grafana ../horreum-integration/target > /dev/null 2>&1

# Force rebuild but keeping the cache
podman untag horreum_keycloak:latest > /dev/null 2>&1

# Get the definition and filter out "--net horreum_default"
podman-compose -p horreum -f docker-compose.yml up -d

cd $ORIGINAL_DIR
