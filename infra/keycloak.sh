#!/bin/bash

delete_grafana() {
    rm /cwd/.grafana
}
trap delete_grafana SIGTERM SIGINT SIGQUIT

delete_grafana

echo "#####################"
echo "# STARTING KEYCLOAK #"
echo "#####################"
export KC_DB_URL=jdbc:postgresql://$DB_ADDR:$DB_PORT/$DB_DATABASE
BUILD_OPTS="--features=upload-scripts"
/opt/keycloak/bin/kc.sh build $BUILD_OPTS || exit 1
/opt/keycloak/bin/kc.sh import --file=/etc/keycloak/imports/keycloak-horreum.json --override=false -Dquarkus.log.level=DEBUG || exit 1
/opt/keycloak/bin/kc.sh start-dev $BUILD_OPTS $EXTRA_OPTIONS

