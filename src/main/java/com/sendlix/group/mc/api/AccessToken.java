package com.sendlix.group.mc.api;


import com.sendlix.api.v1.AuthGrpc;
import com.sendlix.api.v1.AuthProto;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.Base64;
import java.util.logging.Logger;

/**
 * Manages access tokens for the Sendlix API authentication.
 * Handles token creation, validation, and automatic refresh.
 */
public class AccessToken {

    private static final Logger LOGGER = Logger.getLogger(AccessToken.class.getName());
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final long TOKEN_BUFFER_MS = 1000; // 1 second buffer before expiration
    private static final String API_KEY_SEPARATOR = "\\.";
    private static final int EXPECTED_API_KEY_PARTS = 2;

    private final AuthProto.AuthRequest request;
    private AuthProto.AuthResponse response;

    /**
     * Constructs a new AccessToken with the provided secret and key ID.
     *
     * @param secret The secret key for authentication
     * @param keyId  The ID of the API key
     * @throws IllegalArgumentException if secret is null or empty, or keyId is negative
     */
    public AccessToken(String secret, long keyId) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be null or empty");
        }
        if (keyId < 0) {
            throw new IllegalArgumentException("Key ID cannot be negative");
        }

        AuthProto.ApiKey apiKey = AuthProto.ApiKey.newBuilder()
                .setSecret(secret)
                .setKeyID(keyId)
                .build();

        this.request = AuthProto.AuthRequest.newBuilder()
                .setApiKey(apiKey)
                .build();
    }

    /**
     * Creates an AccessToken from a formatted API key string.
     *
     * @param apiKey The API key in format "secret.keyId"
     * @return A new AccessToken instance
     * @throws IllegalArgumentException if the API key format is invalid
     */
    public static AccessToken create(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }

        String[] parts = apiKey.split(API_KEY_SEPARATOR);
        if (parts.length != EXPECTED_API_KEY_PARTS) {
            throw new IllegalArgumentException("Invalid API key format. Expected format: secret.keyId");
        }

        String keySecret = parts[0];
        if (keySecret.isEmpty()) {
            throw new IllegalArgumentException("API key secret cannot be empty");
        }

        long keyId;
        try {
            keyId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid key ID format. Must be a valid number", e);
        }

        return new AccessToken(keySecret, keyId);
    }

    /**
     * Returns the access token for the Sendlix API.
     * If the token is expired or not available, it fetches a new one.
     *
     * @return The access token as a String
     * @throws RuntimeException if token retrieval fails
     */
    public String getAccessToken() {
        if (isTokenValid()) {
            return response.getToken();
        }

        try {
            fetchNewToken();
            return response.getToken();
        } catch (Exception e) {
            LOGGER.severe("Failed to retrieve access token: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve access token", e);
        }
    }

    /**
     * Checks if the current token is valid and not expired.
     *
     * @return true if token is valid, false otherwise
     */
    private boolean isTokenValid() {
        if (response == null) {
            return false;
        }

        long expirationTime = response.getExpires().getSeconds() * 1000;
        long currentTime = System.currentTimeMillis();
        return (expirationTime - currentTime) > TOKEN_BUFFER_MS;
    }

    /**
     * Fetches a new token from the authentication service.
     */
    private void fetchNewToken() {
        AuthGrpc.AuthBlockingStub stub = AuthGrpc.newBlockingStub(Channel.getChannel());
        AuthProto.AuthResponse res = stub.getJwtToken(request);

        //Check if the token has the required scope 'group.insert'
        String payload = res.getToken().split("\\.")[1];
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String decodedPayload = new String(decoder.decode(payload));
        if (!decodedPayload.contains("group.insert"))
            throw new RuntimeException("Api Key does not have the required scope 'group.insert'");

        this.response = res;
        LOGGER.info("Successfully retrieved new access token");
    }

    /**
     * Returns a ClientInterceptor that attaches the access token to the gRPC metadata.
     *
     * @return A ClientInterceptor with authorization header
     */
    public ClientInterceptor getInterceptor() {
        String accessToken = getAccessToken();

        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of(AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, BEARER_PREFIX + accessToken);

        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }
}
