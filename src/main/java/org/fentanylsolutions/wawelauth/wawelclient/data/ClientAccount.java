package org.fentanylsolutions.wawelauth.wawelclient.data;

import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

/**
 * A stored account in the client account manager.
 * <p>
 * Represents an authenticated session with a specific provider.
 * Each account belongs to exactly one provider and holds the tokens
 * and profile data returned by the provider's Yggdrasil API.
 * <p>
 * Uniqueness: when a profile is bound, unique by (provider_name, profile_uuid).
 * When unbound (profile_uuid is null), unique by (provider_name, user_uuid).
 * This allows a single user to have multiple profile bindings on one provider.
 */
public class ClientAccount {

    /**
     * Auto-generated row ID. Primary key.
     */
    private long id;

    /**
     * FK: name of the provider this account belongs to.
     */
    private String providerName;

    /**
     * User UUID from the authenticate response's user.id.
     */
    private String userUuid;

    /**
     * Profile UUID as returned by the auth server. Null until profile bound.
     */
    private UUID profileUuid;

    /**
     * Profile name (player name). Null until profile bound.
     */
    private String profileName;

    /**
     * Access token stored in plaintext.
     */
    private String accessToken;

    /**
     * Optional Microsoft OAuth refresh token.
     * Present for Microsoft-backed Mojang accounts, null otherwise.
     */
    private String refreshToken;

    /**
     * Client token. Persisted to maintain session continuity across restarts.
     */
    private String clientToken;

    /**
     * JSON-serialized user properties from the auth response.
     * e.g. [{"name":"preferredLanguage","value":"en"}]
     */
    private String userPropertiesJson;

    /**
     * Current verification status.
     */
    private AccountStatus status;

    /**
     * Last error message from a failed validation/refresh attempt.
     */
    private String lastError;

    /**
     * Epoch millis of the last error.
     */
    private long lastErrorAt;

    /**
     * Epoch millis of the last refresh/validate attempt.
     */
    private long lastRefreshAttemptAt;

    /**
     * Number of consecutive failures (for backoff calculation).
     */
    private int consecutiveFailures;

    /**
     * Epoch millis when this account was first added.
     */
    private long createdAt;

    /**
     * Epoch millis of the last successful token validation or refresh.
     */
    private long lastValidatedAt;

    /**
     * Epoch millis when the token was originally issued.
     */
    private long tokenIssuedAt;

    /**
     * Local-only cosmetic skin path for offline accounts.
     */
    private String localSkinPath;

    /**
     * Local-only cosmetic skin model for offline accounts.
     */
    private SkinModel localSkinModel;

    /**
     * Local-only cosmetic cape path for offline accounts.
     */
    private String localCapePath;

    public ClientAccount() {}

    // --- Getters/Setters ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(String userUuid) {
        this.userUuid = userUuid;
    }

    public UUID getProfileUuid() {
        return profileUuid;
    }

    public void setProfileUuid(UUID profileUuid) {
        this.profileUuid = profileUuid;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public String getUserPropertiesJson() {
        return userPropertiesJson;
    }

    public void setUserPropertiesJson(String userPropertiesJson) {
        this.userPropertiesJson = userPropertiesJson;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public long getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(long lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public long getLastRefreshAttemptAt() {
        return lastRefreshAttemptAt;
    }

    public void setLastRefreshAttemptAt(long lastRefreshAttemptAt) {
        this.lastRefreshAttemptAt = lastRefreshAttemptAt;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(long lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public long getTokenIssuedAt() {
        return tokenIssuedAt;
    }

    public void setTokenIssuedAt(long tokenIssuedAt) {
        this.tokenIssuedAt = tokenIssuedAt;
    }

    public String getLocalSkinPath() {
        return localSkinPath;
    }

    public void setLocalSkinPath(String localSkinPath) {
        this.localSkinPath = localSkinPath;
    }

    public SkinModel getLocalSkinModel() {
        return localSkinModel;
    }

    public void setLocalSkinModel(SkinModel localSkinModel) {
        this.localSkinModel = localSkinModel;
    }

    public String getLocalCapePath() {
        return localCapePath;
    }

    public void setLocalCapePath(String localCapePath) {
        this.localCapePath = localCapePath;
    }
}
