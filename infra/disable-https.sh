#!/bin/bash

ROOT=$(dirname $0)/..

sed -i '/QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE\|QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_PASSWORD\|QUARKUS_HTTP_INSECURE_REQUESTS\|HORREUM_URL/d' $ROOT/horreum-backend/.env
echo HORREUM_URL=https://localhost:8080 >> $ROOT/horreum-backend/.env

KEYCLOAK_ADMIN_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token -X POST -H 'content-type: application/x-www-form-urlencoded' -d 'username=admin&password=secret&grant_type=password&client_id=admin-cli' | jq -r .access_token)
[ -n "$KEYCLOAK_ADMIN_TOKEN" -a "$KEYCLOAK_ADMIN_TOKEN" != "null" ] || exit 1
AUTH='Authorization: Bearer '$KEYCLOAK_ADMIN_TOKEN
KEYCLOAK_BASEURL=http://localhost:8180/admin/realms/horreum

HORREUM_CLIENTID=$(curl -s $KEYCLOAK_BASEURL/clients -H "$AUTH" | jq -r '.[] | select(.clientId=="horreum") | .id')
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_CLIENTID -H "$AUTH" | jq '.rootUrl |= "http://localhost:8080" | .adminUrl |= "http://localhost:8080" | .redirectUris |= [ "http://localhost:8080/*"] | .webOrigins |= [ "http://localhost:8080" ]' > /tmp/horreum.client
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_CLIENTID -H "$AUTH" -H 'content-type: application/json' -X PUT --data @/tmp/horreum.client

HORREUM_UI_CLIENTID=$(curl -s $KEYCLOAK_BASEURL/clients -H "$AUTH" | jq -r '.[] | select(.clientId=="horreum-ui") | .id')
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_UI_CLIENTID -H "$AUTH" | jq '.rootUrl |= "http://localhost:3000" | .adminUrl |= "http://localhost:3000" | .redirectUris |= [ "http://localhost:8080/*", "http://localhost:3000/*"] | .webOrigins |= [ "http://localhost:8080", "http://localhost:3000" ]' > /tmp/horreum.client
curl -s $KEYCLOAK_BASEURL/clients/$HORREUM_UI_CLIENTID -H "$AUTH" -H 'content-type: application/json' -X PUT --data @/tmp/horreum.client
