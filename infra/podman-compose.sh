#!/bin/bash

# This is a workaround for https://github.com/containers/podman-compose/issues/397

# Remove old containers
CONTAINERS=$(podman ps -a --format json | jq -r '.[] | select(.Labels."com.docker.compose.project" = "horreum").Id')
if [ -n "$CONTAINERS" ]; then
    podman kill $CONTAINERS
    podman rm $CONTAINERS
fi

ORIGINAL_DIR=$(pwd)
cd $(dirname $0)

# Delete leftovers from Docker runs: files owned by root
rm -rf ../horreum-backend/.env ../.grafana ../horreum-integration/target

# Force rebuild but keeping the cache
podman untag horreum_keycloak:latest

# Get the definition and filter out "--net horreum_default"
podman-compose -p horreum -f docker-compose.yml --dry-run up -d | \
    grep -e '^podman' | \
    sed 's/--net horreum_default//' | \
    sed 's/--network-alias [a-zA-Z0-9_-]*//' | \
    sed "s/\(\[\|]\|\"\)/'\1'/g" | bash
cd $ORIGINAL_DIR