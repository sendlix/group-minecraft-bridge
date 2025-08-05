package com.sendlix.group.mc.api;

import com.sendlix.group.mc.config.PluginProperties;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Manages gRPC channel connections to the Sendlix API.
 * Provides a singleton channel instance for efficient connection reuse.
 */
public class Channel {

    private static final Logger LOGGER = Logger.getLogger(Channel.class.getName());
    private static final String ENDPOINT = "api.sendlix.com";
    private static final int PORT = 443;

    private static volatile ManagedChannel channel;
    private static final Object LOCK = new Object();

    /**
     * Private constructor to prevent instantiation.
     */
    private Channel() {
        // Utility class
    }

    /**
     * Returns a singleton gRPC channel instance.
     * Uses double-checked locking for thread safety.
     *
     * @return The gRPC channel for API communication
     */
    public static ManagedChannel getChannel() {
        if (channel == null) {
            synchronized (LOCK) {
                if (channel == null) {
                    channel = createChannel();
                }
            }
        }
        return channel;
    }

    /**
     * Creates a new gRPC channel with proper configuration.
     *
     * @return A configured ManagedChannel
     */
    private static ManagedChannel createChannel() {
        LOGGER.info("Creating new gRPC channel to " + ENDPOINT + ":" + PORT);

        Properties p = PluginProperties.GetProperties();

        String version = p.getProperty("version");
        String name = p.getProperty("name");

        String userAgent = String.format("%s/%s", name, version);

        return NettyChannelBuilder.forAddress(ENDPOINT, PORT)
                .useTransportSecurity()
                .userAgent(userAgent)
                .build();
    }

    /**
     * Shuts down the channel gracefully.
     * Should be called during application shutdown.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (channel != null && !channel.isShutdown()) {
                LOGGER.info("Shutting down gRPC channel");
                channel.shutdown();
                channel = null;
            }
        }
    }
}
