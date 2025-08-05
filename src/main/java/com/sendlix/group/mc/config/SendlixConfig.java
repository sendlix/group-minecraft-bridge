package com.sendlix.group.mc.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Configuration class for storing Sendlix API credentials and settings.
 * Immutable class that holds the API key, group ID, and rate limit settings required for newsletter operations.
 */
public final class SendlixConfig {

    public static final String DEFAULT_API_KEY = "your_api_key_here";
    public static final String DEFAULT_GROUP_ID = "your_group_id_here";
    public static final int DEFAULT_RATE_LIMIT_SECONDS = 5;


    private final String apiKey;
    private final String groupId;
    private final int rateLimitSeconds;
    private final boolean emailValidation;
    private final String emailFrom;

    @Nullable
    private final String privacyPolicyUrl;
    /**
     * Creates a new SendlixConfig instance.
     *
     * @param apiKey  The API key for Sendlix authentication
     * @param groupId The group ID for newsletter operations
     * @param rateLimitSeconds The rate limit in seconds between API calls (default: 5)
     * @param privacyPolicyUrl Optional URL for the privacy policy, can be null
     * @param emailValidation Optional flag if emails need to be validated before adding (default: false)
     * @param emailFrom Optional email address to use as the sender for validation emails, can be null
     * @throws IllegalArgumentException if apiKey or groupId is null or empty, or rateLimitSeconds is invalid
     */
    public SendlixConfig(@Nonnull String apiKey, @Nonnull String groupId, int rateLimitSeconds, String privacyPolicyUrl, boolean emailValidation, String emailFrom) {
        if (apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        if (rateLimitSeconds < 0) {
            throw new IllegalArgumentException("Rate limit seconds cannot be negative");
        }

        this.apiKey = apiKey.trim();
        this.groupId = groupId.trim();
        this.rateLimitSeconds = rateLimitSeconds;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.emailValidation = emailValidation;
        this.emailFrom = emailFrom != null ? emailFrom.trim() : null;
    }

    /**
     * Gets the API key.
     *
     * @return The API key for authentication
     */
    @Nonnull
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Gets the group ID.
     *
     * @return The group ID for newsletter operations
     */
    @Nonnull
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the email address to use as the sender for validation emails.
     *
     * @return The sender email address, or null if not set
     */
    @Nullable
    public String getEmailFrom() {
        return emailFrom;
    }

    /**
     * Gets the rate limit in seconds.
     *
     * @return The rate limit in seconds between API calls
     */
    public int getRateLimitSeconds() {
        return rateLimitSeconds;
    }

    /**
     * Gets the privacy policy URL.
     *
     * @return The privacy policy URL, or null if not set
     */
    @Nullable
    public String getPrivacyPolicyUrl() {
        return privacyPolicyUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        SendlixConfig that = (SendlixConfig) obj;
        return rateLimitSeconds == that.rateLimitSeconds &&
               Objects.equals(apiKey, that.apiKey) &&
               Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, groupId, rateLimitSeconds);
    }

    @Override
    public String toString() {
        return "SendlixConfig{" +
                "apiKey='" + apiKey + '\'' +
                ", groupId='" + groupId + '\'' +
                ", rateLimitSeconds=" + rateLimitSeconds +
                ", privacyPolicyUrl='" + privacyPolicyUrl + '\'' +
                ", emailValidation=" + emailValidation +
                '}';
    }

    /**
     * Checks if email validation is enabled.
     *
     * @return true if email validation is enabled, false otherwise
     */
    public boolean isEmailValidationEnabled() {
        return emailValidation;
    }
}
