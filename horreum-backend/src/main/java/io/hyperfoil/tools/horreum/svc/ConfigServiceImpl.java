package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.Version;
import io.hyperfoil.tools.horreum.api.services.ConfigService;
import org.eclipse.microprofile.config.ConfigProvider;

public class ConfigServiceImpl implements ConfigService {
    @Override
    public KeycloakConfig keycloak() {
        KeycloakConfig config = new KeycloakConfig();
        config.url = getString("horreum.keycloak.url");
        config.realm = getString("horreum.keycloak.realm");
        config.clientId = getString("horreum.keycloak.clientId");
        return config;
    }

    @Override
    public VersionInfo version() {
        VersionInfo info = new VersionInfo();
        info.version = Version.VERSION;
        info.startTimestamp = startTimestamp;
        return info;
    }

    private String getString(String propertyName) {
        return ConfigProvider.getConfig().getValue(propertyName, String.class);
    }

}
