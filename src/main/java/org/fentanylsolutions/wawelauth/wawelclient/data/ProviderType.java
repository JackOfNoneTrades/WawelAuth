package org.fentanylsolutions.wawelauth.wawelclient.data;

/**
 * Distinguishes built-in providers from user-added custom ones.
 * <p>
 * BUILTIN providers (e.g. Mojang) cannot be removed or edited.
 */
public enum ProviderType {
    BUILTIN,
    CUSTOM
}
