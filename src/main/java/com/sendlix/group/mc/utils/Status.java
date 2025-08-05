package com.sendlix.group.mc.utils;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.nio.charset.StandardCharsets;

/**
 * Enumeration representing different email subscription statuses.
 * Used for tracking and communicating the result of newsletter operations.
 */
public enum Status {

    EMAIL_ADDED("email_added"),
    EMAIL_NOT_ADDED("email_not_added"),
    EMAIL_ALREADY_EXISTS("email_already_exists"),
    EMAIL_VERIFICATION_SENT("email_verification_sent"),
    EMAIL_VERIFICATION_FAILED("email_verification_failed");

    private final String statusCode;

    /**
     * Creates a new Status with the specified status code.
     *
     * @param statusCode The string representation of the status
     */
    Status(String statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Gets the status code as a string.
     *
     * @return The status code
     */
    public String getStatusCode() {
        return statusCode;
    }

    /**
     * Gets the status code as bytes for data transmission.
     *
     * @return The status code as UTF-8 encoded bytes
     */
    public byte[] getBytes() {
        return statusCode.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return statusCode;
    }
    /**
     * Sends the status data to the specified player.
     * This method sends the status code as a plugin message to the player's server.
     *
     * @param player The player to send the status data to
     */
    public void sendStatusData(ProxiedPlayer player) {
        if (player.getServer() != null && player.getServer().getInfo() != null) {
            player.getServer().getInfo().sendData("sendlix:newsletter", getBytes());
        }
    }


}
