package com.sendlix.group.mc.core;

import com.sendlix.group.mc.api.AccessToken;
import com.sendlix.group.mc.api.Channel;
import com.sendlix.group.mc.commands.NewsletterCommand;
import com.sendlix.group.mc.config.SendlixConfig;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * Main plugin class for the Sendlix Newsletter BungeeCord plugin.
 * Handles plugin initialization, configuration loading, and API access token management.
 */
public final class SendlixPlugin extends Plugin implements Listener {

    private static final String PLUGIN_CHANNEL = "sendlix:newsletter";
    private static final String CONFIG_FILE_NAME = "config.yml";

    private AccessToken accessToken;
    private SendlixConfig config;

    @Override
    public void onEnable() {
        getProxy().registerChannel(PLUGIN_CHANNEL);

        try {
            config = loadConfiguration();
            if (config == null) {
                getLogger().severe("Configuration could not be loaded. Plugin will be disabled.");
                return;
            }

            initializeAccessToken();
            registerCommandsAndListeners();

            getLogger().info("Sendlix Newsletter plugin enabled successfully");

        } catch (Exception e) {
            getLogger().severe("Failed to enable plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        try {
            NewsletterCommand.shutdown();
            Channel.shutdown();
            getLogger().info("Sendlix Newsletter plugin disabled successfully");
        } catch (Exception e) {
            getLogger().severe("Error during plugin shutdown: " + e.getMessage());
        }
    }

    /**
     * Gets the access token for API authentication.
     *
     * @return The AccessToken instance
     * @throws IllegalStateException if access token is not initialized
     */
    @Nonnull
    public AccessToken getAccessToken() {
        if (accessToken == null) {
            throw new IllegalStateException("Access token is not initialized. Check your configuration.");
        }
        return accessToken;
    }

    /**
     * Gets the configured group ID.
     *
     * @return The group ID string
     * @throws IllegalStateException if configuration is not initialized
     */
    @Nonnull
    public String getGroupId() {
        if (config == null) {
            throw new IllegalStateException("Configuration is not initialized. Check your configuration.");
        }
        return config.getGroupId();
    }

    /**
     * Gets the configured rate limit in seconds.
     *
     * @return The rate limit in seconds between API calls
     * @throws IllegalStateException if configuration is not initialized
     */
    public int getRateLimitSeconds() {
        if (config == null) {
            throw new IllegalStateException("Configuration is not initialized. Check your configuration.");
        }
        return config.getRateLimitSeconds();
    }

    /**
     * Loads and validates the plugin configuration.
     *
     * @return The loaded configuration, or null if loading failed
     */
    @Nullable
    private SendlixConfig loadConfiguration() {
        try {
            File configFile = new File(getDataFolder(), CONFIG_FILE_NAME);

            Configuration config;
            if (configFile.exists())
                config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            else {
                config = new Configuration();
                //Crate new file and folder if they do not exist
                if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                    getLogger().severe("Failed to create plugin data folder: " + getDataFolder().getAbsolutePath());
                    return null;
                }
                if (!configFile.createNewFile()) {
                    getLogger().severe("Failed to create configuration file: " + configFile.getAbsolutePath());
                    return null;
                }
            }

            SendlixConfig conf = validateAndCreateConfig(config);

            ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
            provider.save(config, configFile);

            return  conf;

        } catch (IOException e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            return null;
        }
    }


    private <T> T getAndSetDefault(Configuration config, String key, T defaultValue) {
        T value = config.get(key, defaultValue);
        config.set(key, value);
        return value;
    }

    /**
     * Validates configuration values and creates a SendlixConfig instance.
     *
     * @param config The loaded configuration
     * @return A validated SendlixConfig instance, or null if validation fails
     */
    @Nullable
    private SendlixConfig validateAndCreateConfig(Configuration config) {


        String apiKey = getAndSetDefault(config, "apiKey", SendlixConfig.DEFAULT_API_KEY);
        String groupId = getAndSetDefault(config, "groupId", SendlixConfig.DEFAULT_GROUP_ID);
        int rateLimitSeconds = getAndSetDefault(config, "rateLimitSeconds", SendlixConfig.DEFAULT_RATE_LIMIT_SECONDS);
        String privacyPolicy = config.getString("privacyPolicyUrl", null);


        if (isDefaultValue(apiKey, SendlixConfig.DEFAULT_API_KEY) || isDefaultValue(groupId, SendlixConfig.DEFAULT_GROUP_ID)) {
            getLogger().severe("Please update your config.yml with valid apiKey and groupId.");
            return null;
        }

        if (rateLimitSeconds < 0) {
            getLogger().warning("Invalid rateLimitSeconds value: " + rateLimitSeconds + ". Using default: " + SendlixConfig.DEFAULT_RATE_LIMIT_SECONDS);
            rateLimitSeconds = SendlixConfig.DEFAULT_RATE_LIMIT_SECONDS;
        }



        return new SendlixConfig(apiKey.trim(), groupId.trim(), rateLimitSeconds , privacyPolicy);
    }

    /**
     * Checks if a value is the default placeholder value.
     *
     * @param value        The value to check
     * @param defaultValue The default placeholder value
     * @return true if the value is the default placeholder
     */
    private boolean isDefaultValue(String value, String defaultValue) {
        return Objects.equals(value, defaultValue);
    }

    /**
     * Initializes the access token with the configured API key.
     */
    private void initializeAccessToken() {
        try {
            accessToken = AccessToken.create(config.getApiKey());

            // Test the token by attempting to retrieve it
            String token = accessToken.getAccessToken();
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Retrieved token is null or empty");
            }

            getLogger().info("Access token initialized successfully");

        } catch (Exception e) {
            getLogger().severe("Failed to initialize access token: " + e.getMessage());
            throw new RuntimeException("Invalid API key in configuration", e);
        }
    }

    /**
     * Registers plugin commands.
     */
    private void registerCommandsAndListeners() {
        NewsletterCommand nlc = new NewsletterCommand(this);
        getProxy().getPluginManager().registerCommand(this, nlc );
        getProxy().getPluginManager().registerListener(this, nlc);
        getLogger().info("Commands registered successfully");
    }

    public SendlixConfig getConfig() {
        return config;
    }
}
