package io.hyperfoil.tools.horreum.api;

import java.io.IOException;
import java.util.Properties;

import io.quarkus.logging.Log;

public class Version {
    public static final String getVersion() {
        final Properties properties = new Properties();
        try {
            properties.load(Version.class.getClassLoader().getResourceAsStream("build.properties"));
            return properties.getProperty("horreum.version");
        } catch (IOException e) {
            Log.error("Failed to load version.properties", e);
        }
        return null;
    }
}
