#!/bin/bash

# This is a workaround for https://github.com/containers/podman-compose/issues/397

# Remove old containers
CONTAINERS=$(podman ps -a --format json | jq -r '.[] | select(.Labels."com.docker.compose.project" = "horreum").Id')
if [ -n "$CONTAINERS" ]; then
    podman kill $CONTAINERS
    podman rm $CONTAINERS
fi

# Delete leftovers from Docker runs: files owned by root
rm horreum-backend/.env .grafana

# Get the definition and filter out "--net horreum_default"
podman-compose -p horreum -f $(dirname $0)/podman-compose.yml --dry-run up -d | \
    grep -e '^podman' | sed 's/--net horreum_default//' | \
    sed "s/\(\[\|]\|\"\)/'\1'/g" | bash