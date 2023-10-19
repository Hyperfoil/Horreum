#!/bin/bash

# This script fills the development instance of Horreum with some dummy data.
# Configuring this through REST API is more stable than filling database with SQL commands,
# but in the long term we should use configuration import for that (we might even load it
# through a property in dev mode): https://github.com/Hyperfoil/Horreum/issues/289

set -o pipefail

EX=$(dirname $0)/example-data
API="http://localhost:8080/api"
KEYCLOAK_URL=$( curl -k -s $API/config/keycloak | jq -r '.url')
TOKEN=$(curl -s -X POST $KEYCLOAK_URL/realms/horreum/protocol/openid-connect/token \
    -H 'content-type: application/x-www-form-urlencoded' \
    -d 'username=user&password=secret&grant_type=password&client_id=horreum-ui' \
    | jq -r .access_token)

fail() {
  echo "##########################" 1>&2
  echo "#      IMPORT FAILED     #" 1>&2
  echo "##########################" 1>&2
  exit 1
}

post() {
  if [ "$1" = "-s" ]; then
    PIPE_TO="true"
    shift
  else
    PIPE_TO="cat -"
  fi
  if [[ "$2" == *.json ]]; then
    if [[ "$2" == /* ]]; then
      BODY=@$2
    else
      BODY=@${EX}/$2
    fi
  else
    BODY="$2"
  fi
  if [ -n "$3" ]; then
    TYPE=$3
  else
    TYPE="application/json"
  fi
  echo "POST $1" >&2
  OUTPUT=$(mktemp)
  curl -s ${API}$1 -H 'Authorization: Bearer '$TOKEN -H 'Content-Type: '$TYPE -d "$BODY" > $OUTPUT
  if [ $? != 0 ]; then
     cat $OUTPUT >&2
     echo "" >&2
     fail
  else
     cat $OUTPUT | $PIPE_TO
  fi
}

get() {
  curl -s ${API}$1 -H 'Authorization: Bearer '$TOKEN -H 'Accept: application/json' || fail
}

delete() {
  echo "DELETE $1"
  curl -s ${API}$1 -H 'Authorization: Bearer '$TOKEN -X DELETE || fail
}

combine() {
  local FILE=$(mktemp)
  if [[ "$1" == /* ]]; then
    BASE=$1
  else
    BASE=$EX/$1
  fi
  shift
  cat $BASE | jq -r $@ > $FILE
  mv $FILE $FILE.json
  echo $FILE.json
}

function deleteAll() {
  local DELETE_URL=$2
  if [ -z "$DELETE_URL" ]; then
    DELETE_URL=$1
  fi
  for ID in $(get $1 | jq -r '.[].id'); do
    if [ -n "$ID" ]; then
      delete $DELETE_URL/$ID
    fi
  done
}

function requireId() {
  if [ -z "${!1}" ]; then
    echo "Empty $1 - previous request probably failed" >&2
    fail
  elif [[ ! ( "${!1}" =~ ^[0-9]+$ ) ]]; then
    echo "This is not a numeric ID (likely an error message): "${!1} >&2
    fail
  fi
}

if [ "$DELETE" = "true" ]; then
  deleteAll /test
  deleteAll /schema
  deleteAll /action/list /action
  deleteAll /action/allowedSites
fi

if [ "$IMPORT" != "false" ]; then
  post -s /schema hyperfoil_schema.json
  post -s /config/backends elastic-backend.json
  post -s /test protected_test.json

  ACME_BENCHMARK_SCHEMA_ID=$(post /schema acme_benchmark_schema.json) || exit 1
  requireId ACME_BENCHMARK_SCHEMA_ID
  ACME_HORREUM_SCHEMA_ID=$(post /schema acme_horreum_schema.json) || exit 1
  requireId ACME_HORREUM_SCHEMA_ID
  ACME_TRANSFORMER_ID=$(post /schema/$ACME_BENCHMARK_SCHEMA_ID/transformers acme_transformer.json) || exit 1
  requireId ACME_TRANSFORMER_ID
  ROADRUNNER_TEST_ID=$(post /test roadrunner_test.json | jq -r '.id') || exit 1
  requireId ROADRUNNER_TEST_ID
  post -s /test/$ROADRUNNER_TEST_ID/transformers '['$ACME_TRANSFORMER_ID']'

  post -s /run/test?test=$ROADRUNNER_TEST_ID roadrunner_run.json

  post -s /schema/$ACME_HORREUM_SCHEMA_ID/labels test_label.json
  post -s /schema/$ACME_HORREUM_SCHEMA_ID/labels throughput_label.json
  post -s /alerting/variables?test=$ROADRUNNER_TEST_ID roadrunner_variables.json
  post -s /subscriptions/$ROADRUNNER_TEST_ID roadrunner_watch.json
  post -s '/notifications/settings?name=user&team=false' '[{ "method": "email", "data": "dummy@example.com" }]'

  post -s /action/allowedSites 'http://example.com' 'text/plain'
  post -s /action new_test_action.json
  NEW_RUN=$(combine new_run_action.json '.testId='$ROADRUNNER_TEST_ID)
  post -s /action $NEW_RUN

  echo "########################"
  echo "#   IMPORT SUCEEEDED   #"
  echo "########################"
fi
