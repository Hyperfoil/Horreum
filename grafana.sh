#!/bin/bash
ENVVAR_FILE=/var/lib/grafana/.grafana
while [ ! -f $ENVVAR_FILE ]; do
  sleep 5;
done
source $ENVVAR_FILE
# Prevent using of secret from a previous docker-compose instance
rm -f $ENVVAR_FILE
export GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET
/run.sh