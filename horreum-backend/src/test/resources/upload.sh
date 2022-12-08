#!/bin/bash
# This script is intended only as a helper during development for easy upload of runs.
# See https://horreum.hyperfoil.io/docs/upload.html for more info about uploads.

if [ $# -lt 2 -o $# -gt 3 -o ! -f "$2" ]; then
  echo "Usage: upload.sh TestName /path/to/data.json [ /path/to/metadata.json ]" >&2
  exit 1
fi
if [ $# -eq 3 -a ! -f "$3" ]; then
  echo "Metadata file $3 does not exist or is not a regular file". >&2
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
TEST=$(echo $1 | jq -sRr "@uri")
START=$(date -Iseconds | tr -d '\n' | jq -sRr "@uri" )
STOP=$START
OWNER='dev-team'
ACCESS='PUBLIC'
set -x
if [ $# -eq 2 ]; then
  curl $HORREUM_URL'/api/run/data?test='"$TEST"'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
      -s -X POST -H 'content-type: application/json' \
      -H 'Authorization: Bearer '$TOKEN \
      -d @$2
else
  curl -v $HORREUM_URL'/api/run/data?test='"$TEST"'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
      -s -X POST \
      -H 'Authorization: Bearer '$TOKEN \
      -F "data=@$2;type=application/json" -F "metadata=@$3;type=application/json"
fi