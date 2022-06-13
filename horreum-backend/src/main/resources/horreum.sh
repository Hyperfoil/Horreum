#!/bin/sh

JAVA_OPTIONS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager -Djava.security.egd=file:/dev/urandom $JAVA_OPTIONS"
if [ -n "$QUARKUS_DATASOURCE_USERNAME" ]; then
  # When the property is set using environment variables it is not correctly propagated
  # to Liquibase changeLog.xml
  JAVA_OPTIONS="$JAVA_OPTIONS -Dquarkus.datasource.username=$QUARKUS_DATASOURCE_USERNAME"
fi
if [ -n "$HORREUM_DB_SECRET" ]; then
  # Same as above, for Liquibase.
  JAVA_OPTIONS="$JAVA_OPTIONS -Dhorreum.db.secret=$HORREUM_DB_SECRET"
fi

echo "Starting Horreum with JAVA_OPTIONS: $JAVA_OPTIONS"
java $JAVA_OPTIONS -jar quarkus-run.jar
