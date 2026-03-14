package org.fentanylsolutions.wawelauth.wawelclient.data;

/**
 * Verification status of a stored account's token.
 * <p>
 * VALID: Token was verified against the provider and is good.
 * <p>
 * REFRESHED: Token was refreshed (new accessToken obtained).
 * <p>
 * UNVERIFIED: Provider unreachable; using cached profile data.
 * <p>
 * May connect to offline-mode servers.
 * <p>
 * UNAUTHED: Explicit offline-mode account with vanilla OfflinePlayer UUID.
 * <p>
 * EXPIRED: Token failed validation and refresh; re-authentication required.
 */
public enum AccountStatus {
    VALID,
    REFRESHED,
    UNVERIFIED,
    UNAUTHED,
    EXPIRED
}
