#!/bin/bash

ROOT=$(dirname $0)/..

echo QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE=$ROOT/src/test/resources/keystore.jks >> $ROOT/horreum-backend/.env
echo QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_PASSWORD=secret >> $ROOT/horreum-backend/.env
echo QUARKUS_HTTP_INSECURE_REQUESTS=disabled >> $ROOT/horreum-backend/.env
echo HORREUM_URL=https://localhost:8443 >> $ROOT/horreum-backend/.env

KEYCLOAK_ADMIN_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token -X POST -H 'content-type: application/x-www-form-urlencoded' -d 'username=admin&password=secret&grant_type=password&client_id=admin-cli' | jq -r .access_token)
[ -n "$KEYCLOAK_ADMIN_TOKEN" -a "$KEYCLOAK_ADMIN_TOKEN" != "null" ] || exit 1
AUTH='Authorization: Bearer '$KEYCLOAK_ADMIN_TOKEN
KEYCLOAK_BASEURL=http://localhost:8180/admin/realms/horreum

HORREUM_CLIENTID=$(curl -s $KEYCLOAK_BASEURL/clients -H "$AUTH" | jq -r '.[] | select(.clientId=="horreum") | .id')
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_CLIENTID -H "$AUTH" | jq '.rootUrl |= "https://localhost:8443" | .adminUrl |= "https://localhost:8443" | .redirectUris |= [ "https://localhost:8443/*"] | .webOrigins |= [ "https://localhost:8443" ]' > /tmp/horreum.client
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_CLIENTID -H "$AUTH" -H 'content-type: application/json' -X PUT --data @/tmp/horreum.client

HORREUM_UI_CLIENTID=$(curl -s $KEYCLOAK_BASEURL/clients -H "$AUTH" | jq -r '.[] | select(.clientId=="horreum-ui") | .id')
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_UI_CLIENTID -H "$AUTH" | jq '.rootUrl |= "https://localhost:3000" | .adminUrl |= "https://localhost:3000" | .redirectUris |= [ "https://localhost:8443/*", "https://localhost:3000/*"] | .webOrigins |= [ "https://localhost:8443", "https://localhost:3000" ]' > /tmp/horreum.client
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_UI_CLIENTID -H "$AUTH" -H 'content-type: application/json' -X PUT --data @/tmp/horreum.client
