package org.fentanylsolutions.wawelauth.wawelclient;

/**
 * Duck typing interface implemented by MixinServerData.
 * Extends ServerData with per-server WawelAuth account selection.
 *
 * Cast any ServerData instance to this interface to access the
 * WawelAuth fields: {@code IServerDataExt ext = (IServerDataExt) serverData;}
 */
public interface IServerDataExt {

    /** Row ID of the selected WawelAuth account, or -1 if none. */
    long getWawelAccountId();

    void setWawelAccountId(long id);

    /** Provider name for the selected account, or null. */
    String getWawelProviderName();

    void setWawelProviderName(String name);

    /** Persisted local-auth public key fingerprint for this server entry, or null. */
    String getWawelLocalAuthFingerprint();

    void setWawelLocalAuthFingerprint(String fingerprint);

    /** Persisted local-auth API root for this server entry, or null. */
    String getWawelLocalAuthApiRoot();

    void setWawelLocalAuthApiRoot(String apiRoot);

    /** Persisted local-auth signature public key body for this server entry, or null. */
    String getWawelLocalAuthPublicKeyBase64();

    void setWawelLocalAuthPublicKeyBase64(String publicKeyBase64);

    /** Original server IP/host this WawelAuth binding was established against, or null. */
    String getWawelOriginalServerIp();

    void setWawelOriginalServerIp(String originalServerIp);

    /**
     * Latest capability negotiation result from server ping.
     * Runtime-only (must never be persisted to NBT).
     */
    ServerCapabilities getWawelCapabilities();

    void setWawelCapabilities(ServerCapabilities capabilities);
}
