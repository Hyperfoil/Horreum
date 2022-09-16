#!/bin/bash
# This script is intended only as a helper during development for easy upload of runs.
# See https://horreum.hyperfoil.io/docs/upload.html for more info about uploads.

if [ $# -ne 2 -o ! -f $2 ]; then
  echo "Usage: upload.sh TestName /path/to/file.json"
  exit 1
fi

KEYCLOAK_URL=http://localhost:8180
HORREUM_USER=user
HORREUM_PASSWORD=secret
TOKEN=$(curl -s -X POST $KEYCLOAK_URL/realms/horreum/protocol/openid-connect/token \
    -H 'content-type: application/x-www-form-urlencoded' \
    -d 'username='$HORREUM_USER'&password='$HORREUM_PASSWORD'&grant_type=password&client_id=horreum-ui' \
    | jq -r .access_token)
HORREUM_URL=http://localhost:8080
TEST=$1
START=$(date -Iseconds | tr -d '\n' | jq -sRr "@uri" )
STOP=$START
OWNER='dev-team'
ACCESS='PUBLIC'
curl $HORREUM_URL'/api/run/data?test='$TEST'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
    -s -X POST -H 'content-type: application/json' \
    -H 'Authorization: Bearer '$TOKEN \
    -d @$2
