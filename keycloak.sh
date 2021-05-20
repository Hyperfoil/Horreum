#!/bin/bash

delete_grafana() {
    rm /cwd/.grafana
}
trap delete_grafana SIGTERM SIGINT SIGQUIT

delete_grafana
/opt/jboss/tools/docker-entrypoint.sh \
  -Dkeycloak.profile.feature.upload_scripts=enabled \
  -Dkeycloak.migration.action=import \
  -Dkeycloak.migration.provider=singleFile \
  -Dkeycloak.migration.file=/etc/keycloak/imports/keycloak-horreum.json \
  -Dkeycloak.migration.strategy=IGNORE_EXISTING \
  -Djboss.socket.binding.port-offset=100 \
  -Djboss.bind.address=127.0.0.1 \
  -Djboss.bind.address.private=127.0.0.1
