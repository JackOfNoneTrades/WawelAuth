package org.fentanylsolutions.wawelauth.wawelcore.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for an upstream/fallback auth server.
 * <p>
 * When the local server cannot resolve a session or profile,
 * it queries fallback servers in order. This enables "vanilla-like"
 * behavior by configuring Mojang as the sole fallback.
 */
public class FallbackServer {

    private String name = "";
    private String sessionServerUrl = "";
    private String accountUrl = "";
    private String servicesUrl = "";
    private List<String> skinDomains = new ArrayList<>();
    private int cacheTtlSeconds = 300;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSessionServerUrl() {
        return sessionServerUrl;
    }

    public void setSessionServerUrl(String sessionServerUrl) {
        this.sessionServerUrl = sessionServerUrl;
    }

    public String getAccountUrl() {
        return accountUrl;
    }

    public void setAccountUrl(String accountUrl) {
        this.accountUrl = accountUrl;
    }

    public String getServicesUrl() {
        return servicesUrl;
    }

    public void setServicesUrl(String servicesUrl) {
        this.servicesUrl = servicesUrl;
    }

    public List<String> getSkinDomains() {
        if (skinDomains == null) skinDomains = new ArrayList<>();
        return skinDomains;
    }

    public void setSkinDomains(List<String> skinDomains) {
        this.skinDomains = skinDomains;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
