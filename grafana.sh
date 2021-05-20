#!/bin/bash
set -x
ENVVAR_FILE=/cwd/.grafana
while [ ! -f $ENVVAR_FILE ]; do
  sleep 5;
done
source $ENVVAR_FILE
export GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET
/run.sh