#!/bin/bash

# generate self-signed certificate

openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes -keyout src/main/resources/keycloak-tls.key -out src/main/resources/keycloak-tls.crt -subj "/CN=testcontainer"

# combine private key and certificate into a pkcs12 keystore

openssl pkcs12 -export -password pass:password -inkey src/main/resources/keycloak-tls.key -in src/main/resources/keycloak-tls.crt -out src/main/resources/horreum-dev-keycloak.pkcs12

# verify the keystore

keytool -list -v -keystore src/main/resources/horreum-dev-keycloak.pkcs12 -storepass password -storetype PKCS12
