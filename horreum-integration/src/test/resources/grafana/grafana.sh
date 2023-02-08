#!/bin/bash

if [ $(id -u) -eq 0 ]; then
  cp /etc/hosts /etc/hosts.copy
  grep -v -e '::1' /etc/hosts.copy > /etc/hosts
  echo "Removing IPv6 from /etc/hosts"
fi

# Wait a bit to make sure keycloak deleted the *old* .grafana
ENVVAR_FILE=/cwd/.grafana
while [ ! -f $ENVVAR_FILE ] || (find $ENVVAR_FILE -mmin +1 | grep . > /dev/null); do
  echo "Waiting for an updated $ENVVAR_FILE"
  echo "If you restarted this container please restart the app-init container as well."
  sleep 5;
done
source $ENVVAR_FILE
export GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET
echo "Starting Grafana..."
/run.sh