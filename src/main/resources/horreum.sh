#!/bin/sh

if [ -z "$JAVA_OPTIONS" ]; then
  JAVA_OPTIONS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
fi
if [ -n "$QUARKUS_DATASOURCE_USERNAME" ]; then
  # When the property is set using environment variables it is not correctly propagated
  # to Liquibase changeLog.xml
  JAVA_OPTIONS="$JAVA_OPTIONS -Dquarkus.datasource.username=$QUARKUS_DATASOURCE_USERNAME"
fi

echo "Starting Horreum with JAVA_OPTIONS: $JAVA_OPTIONS"
java $JAVA_OPTIONS -jar quarkus-run.jar
