package com.sendlix.group.mc.config;

import java.io.IOException;

public class PluginProperties {

    private static java.util.Properties properties;


    public static java.util.Properties GetProperties() {
        if (properties == null) {
            properties = new java.util.Properties();
            try {
                // Load properties from the classpath
                properties.load(
                        PluginProperties.class.getClassLoader().getResourceAsStream("plugin.properties")
                );
            } catch (IOException e) {
                System.err.println("Failed to load properties file: " + e.getMessage());
            }
        }
        return properties;
    }

}
