#!/bin/sh

if [ -z "$JAVA_OPTIONS" ]; then
  JAVA_OPTIONS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
fi

echo "Starting Horreum with JAVA_OPTIONS: $JAVA_OPTIONS"
java $JAVA_OPTIONS -jar quarkus-run.jar
