package org.fentanylsolutions.wawelauth.wawelcore.data;

/**
 * Token states as defined by the Yggdrasil specification.
 * <p>
 * VALID: Token is usable for authentication, joining servers, etc.
 * <p>
 * TEMPORARILY_INVALID: Token is currently unusable but can return to valid.
 * Primary use case: after a profile rename, existing tokens are marked
 * temporarily invalid to force clients to refresh and obtain the updated
 * profile name. A temporarily invalid token behaves as invalid in all
 * API operations except refresh (where it can be exchanged for a new valid token).
 * <p>
 * INVALID: Token is permanently unusable (explicitly revoked or expired).
 * <p>
 * Invalid tokens should be cleaned up periodically.
 */
public enum TokenState {

    VALID,
    TEMPORARILY_INVALID,
    INVALID
}
