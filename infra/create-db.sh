#!/bin/bash

psql -c "SELECT 1;" || exit 1
if ! psql -t -c "SELECT 1 FROM pg_roles WHERE rolname = 'keycloak';" | grep -q 1; then
  psql -c "CREATE ROLE keycloak noinherit login password 'secret';"
fi
if ! psql -t -c "SELECT 1 FROM pg_database WHERE datname = 'keycloak';" | grep -q 1; then
  psql -c "CREATE DATABASE keycloak WITH OWNER = 'keycloak';"
fi
if ! psql -t -c "SELECT 1 FROM pg_roles WHERE rolname = 'appuser';" | grep -q 1; then
  psql -c "CREATE ROLE appuser noinherit login password 'secret';"
fi