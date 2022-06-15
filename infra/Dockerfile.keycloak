FROM quay.io/keycloak/keycloak:18.0.0
COPY keycloak-horreum.json /opt/keycloak/data/import/
COPY keycloak.sh /opt/keycloak/bin/keycloak.sh

# Consumed by Keycloak natively
ENV KEYCLOAK_ADMIN=admin
ENV KEYCLOAK_ADMIN_PASSWORD=secret
ENV KC_CACHE=local
ENV KC_DB=postgres
ENV KC_DB_USERNAME=keycloak
ENV KC_DB_PASSWORD=secret
ENV KC_HTTP_ENABLED=true
ENV KC_HTTP_PORT=8180
ENV KC_HTTP_ENABLED=true
ENV KC_HOSTNAME_STRICT=false

# Used in keycloak.sh
ENV DB_PORT=5432
ENV DB_DATABASE=keycloak
ENV KC_DB_URL=jdbc:postgresql://$DB_ADDR:$DB_PORT/$DB_DATABASE
RUN /opt/keycloak/bin/kc.sh build
ENTRYPOINT /opt/keycloak/bin/keycloak.sh