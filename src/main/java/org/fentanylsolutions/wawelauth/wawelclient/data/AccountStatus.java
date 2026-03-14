package org.fentanylsolutions.wawelauth.wawelclient.data;

/**
 * Verification status of a stored account's token.
 * <p>
 * VALID: Token was verified against the provider and is good.
 * REFRESHED: Token was refreshed (new accessToken obtained).
 * UNVERIFIED: Provider unreachable; using cached profile data.
 * May connect to offline-mode servers.
 * UNAUTHED: Explicit offline-mode account with vanilla OfflinePlayer UUID.
 * EXPIRED: Token failed validation and refresh; re-authentication required.
 */
public enum AccountStatus {
    VALID,
    REFRESHED,
    UNVERIFIED,
    UNAUTHED,
    EXPIRED
}
