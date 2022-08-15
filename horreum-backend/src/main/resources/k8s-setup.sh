#!/bin/sh
set -e -x
# This file is used for setting up other services in K8s or Openshift
export TOKEN=$(curl -s $CA_CERT_ARG $KC_URL/realms/master/protocol/openid-connect/token \
  -X POST -H 'content-type: application/x-www-form-urlencoded' \
  -d 'username='$KEYCLOAK_USER'&password='$KEYCLOAK_PASSWORD'&grant_type=password&client_id=admin-cli' | jq -r .access_token)
[ -n "$TOKEN" ] || exit 1

fetch() {
  curl -s $([ "$NOFAIL" = "true" ] || echo "--fail") $CA_CERT_ARG -H 'Authorization: Bearer '$TOKEN "$@"
}

CLIENTID=$(fetch $KC_URL/admin/realms/horreum/clients | jq -r '.[] | select(.clientId=="horreum") | .id')
[ -n "$CLIENTID" ] || exit 1
CLIENTSECRET=$(fetch $KC_URL/admin/realms/horreum/clients/$CLIENTID/client-secret -X POST | jq -r '.value')
[ -n "$CLIENTSECRET" ] || exit 1;
echo $CLIENTSECRET > /etc/horreum/imports/clientsecret

update_urls() {
  CLIENTNAME=$1
  URL=$2
  CLIENTID=$(fetch $KC_URL/admin/realms/horreum/clients | jq -r '.[] | select(.clientId=="'${CLIENTNAME}'") | .id')
  [ -n "$CLIENTID" ] || exit 1
  fetch $KC_URL/admin/realms/horreum/clients/$CLIENTID > /tmp/client.json
  jq '.rootUrl = "'$URL'/" | .adminUrl = "'$URL'" | .webOrigins = [ "'$URL'" ] | .redirectUris = [ "'$URL'/*"]' /tmp/client.json > /tmp/updated.json
  fetch $KC_URL/admin/realms/horreum/clients/$CLIENTID -X PUT -H 'content-type: application/json' -d @/tmp/updated.json
}

update_urls horreum-ui $APP_URL
update_urls grafana $GRAFANA_URL

# Create admin user in Keycloak
REALM_URL=$KC_URL/admin/realms/horreum
ADMIN_ROLE_ID=$(fetch $REALM_URL/roles/admin | jq -r '.id')
[ -n "$ADMIN_ROLE_ID" ] || exit 1;
# Expecting 409 conflict if the user is present (on restart)
NOFAIL=true fetch $REALM_URL/users -X POST \
  -d '{"username":"'$ADMIN_USERNAME'","enabled":true,"firstName":"Admin","credentials":[{"type":"password","value":"'$ADMIN_PASSWORD'"}],"email":"admin@example.com"}' -H 'content-type: application/json'
ADMIN_ID=$(fetch $REALM_URL/users | jq -r '.[] | select(.username=="'$ADMIN_USERNAME'") | .id')
[ -n "$ADMIN_ID" ] || exit 1
fetch $REALM_URL/users/$ADMIN_ID/role-mappings/realm -H 'content-type: application/json' -X POST -d '[{"id":"'$ADMIN_ROLE_ID'","name":"admin"}]'

ACCOUNT_CLIENTID=$(fetch $REALM_URL/clients | jq -r '.[] | select(.clientId=="account") | .id')
VIEW_PROFILE_ID=$(fetch $REALM_URL/clients/${ACCOUNT_CLIENTID}/roles/view-profile | jq -r '.id')
[ -n "$ACCOUNT_CLIENTID" -a -n "$VIEW_PROFILE_ID" ] || exit 1
fetch $REALM_URL/users/$ADMIN_ID/role-mappings/clients/$ACCOUNT_CLIENTID -H 'content-type: application/json' -X POST -d '[{"id":"'$VIEW_PROFILE_ID'","name":"view-profile"}]'

# Convert PEM certificates to a Java keystore
keytool -keystore /etc/horreum/imports/service-ca.keystore -storepass password -noprompt -trustcacerts -import -alias service-ca -file /etc/ssl/certs/service-ca.crt