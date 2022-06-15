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
# Automatically imports contents of /opt/keycloak/data/import/
/opt/keycloak/bin/kc.sh start-dev --import-realm $EXTRA_OPTIONS

