#!/bin/bash

echo "# Env generated by app-init.sh" > /cwd/.env
if [ "$CONTAINER_RUNTIME" = "podman" ]; then
  echo "QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/horreum" >> /cwd/.env
  echo "QUARKUS_DATASOURCE_MIGRATION_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/horreum" >> /cwd/.env
  echo "QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/auth/realms/horreum" >> /cwd/.env
  KEYCLOAK_HOST=127.0.0.1
  HORREUM_URL=http://127.0.0.1:8080
else
  KEYCLOAK_HOST=172.17.0.1
  HORREUM_URL=http://172.17.0.1:8080
fi


while ! curl --fail $KEYCLOAK_HOST:8180; do
  sleep 5;
done
KEYCLOAK_ADMIN_TOKEN=$(curl -s $KEYCLOAK_HOST:8180/auth/realms/master/protocol/openid-connect/token -X POST -H 'content-type: application/x-www-form-urlencoded' -d 'username=admin&password=secret&grant_type=password&client_id=admin-cli' | jq -r .access_token)
[ -n "$KEYCLOAK_ADMIN_TOKEN" -a "$KEYCLOAK_ADMIN_TOKEN" != "null" ] || exit 1
AUTH='Authorization: Bearer '$KEYCLOAK_ADMIN_TOKEN
KEYCLOAK_BASEURL=$KEYCLOAK_HOST:8180/auth/admin/realms/horreum

# Obtain client secrets for both Horreum and Grafana
HORREUM_CLIENTID=$(curl -s $KEYCLOAK_BASEURL/clients -H "$AUTH" | jq -r '.[] | select(.clientId=="horreum") | .id')
HORREUM_CLIENTSECRET=$(curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_CLIENTID/client-secret -X POST -H "$AUTH" | jq -r '.value')
[ -n "$HORREUM_CLIENTSECRET" -a "$HORREUM_CLIENTSECRET" != "null" ] || exit 1
echo QUARKUS_OIDC_CREDENTIALS_SECRET=$HORREUM_CLIENTSECRET >> /cwd/.env
chmod a+w /cwd/.env
GRAFANA_CLIENTID=$(curl -s $KEYCLOAK_BASEURL/clients -H "$AUTH" | jq -r '.[] | select(.clientId=="grafana") | .id')
GRAFANA_CLIENTSECRET=$(curl -s $KEYCLOAK_BASEURL/clients/$GRAFANA_CLIENTID/client-secret -X POST -H "$AUTH" | jq -r '.value')
[ -n "$GRAFANA_CLIENTSECRET" -a "$GRAFANA_CLIENTSECRET" != "null" ] || exit 1
echo GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET=$GRAFANA_CLIENTSECRET > /cwd/.grafana
chmod a+w /cwd/.grafana

# Create roles and example user in Keycloak
UPLOADER_ID=$(curl -s $KEYCLOAK_BASEURL/roles/uploader -H "$AUTH"  | jq -r '.id')
TESTER_ID=$(curl -s $KEYCLOAK_BASEURL/roles/tester -H "$AUTH" | jq -r '.id')
VIEWER_ID=$(curl -s $KEYCLOAK_BASEURL/roles/viewer -H "$AUTH" | jq -r '.id')
ADMIN_ID=$(curl -s $KEYCLOAK_BASEURL/roles/admin -H "$AUTH" | jq -r '.id')
curl -s $KEYCLOAK_BASEURL/roles -H "$AUTH" -H 'content-type: application/json' -X POST -d '{"name":"dev-team"}'
TEAM_ID=$(curl -s $KEYCLOAK_BASEURL/roles/dev-team -H "$AUTH" | jq -r '.id')
curl -s $KEYCLOAK_BASEURL/roles -H "$AUTH" -H 'content-type: application/json' -X POST -d '{"name":"dev-uploader","composite":true}'
TEAM_UPLOADER_ID=$(curl -s $KEYCLOAK_BASEURL/roles/dev-uploader -H "$AUTH" | jq -r '.id')
curl -s $KEYCLOAK_BASEURL/roles/dev-uploader/composites -H "$AUTH" -H 'content-type: application/json' -X POST -d '[{"id":"'$TEAM_ID'"},{"id":"'$UPLOADER_ID'"}]'
curl -s $KEYCLOAK_BASEURL/roles -H "$AUTH" -H 'content-type: application/json' -X POST -d '{"name":"dev-tester","composite":true}'
TEAM_TESTER_ID=$(curl -s $KEYCLOAK_BASEURL/roles/dev-tester -H "$AUTH" | jq -r '.id')
curl -s $KEYCLOAK_BASEURL/roles/dev-tester/composites -H "$AUTH" -H 'content-type: application/json' -X POST -d '[{"id":"'$TEAM_ID'"},{"id":"'$TESTER_ID'"},{"id":"'$VIEWER_ID'"}]'
curl -s $KEYCLOAK_BASEURL/users -H "$AUTH" -X POST -d '{"username":"user","enabled":true,"credentials":[{"type":"password","value":"secret"}],"email":"user@example.com"}' -H 'content-type: application/json'
USER_ID=$(curl -s $KEYCLOAK_BASEURL/users -H "$AUTH" | jq -r '.[] | select(.username="user") | .id')
curl -s $KEYCLOAK_BASEURL/users/$USER_ID/role-mappings/realm -H "$AUTH" -H 'content-type: application/json' -X POST -d '[{"id":"'$TEAM_UPLOADER_ID'","name":"dev-uploader"},{"id":"'$TEAM_TESTER_ID'","name":"dev-tester"},{"id":"'$ADMIN_ID'","name":"admin"}]'
