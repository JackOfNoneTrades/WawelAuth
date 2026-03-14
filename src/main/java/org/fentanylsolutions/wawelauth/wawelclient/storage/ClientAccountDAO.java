package org.fentanylsolutions.wawelauth.wawelclient.storage;

import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;

/**
 * Data access interface for {@link ClientAccount} entities.
 * <p>
 * Manages the stored client-side accounts (authenticated sessions
 * with Yggdrasil providers).
 */
public interface ClientAccountDAO {

    /**
     * Find an account by its row ID. Returns null if not found.
     */
    ClientAccount findById(long id);

    /**
     * Find an account by provider name and user UUID (for unbound accounts).
     * Returns null if not found.
     */
    ClientAccount findByProviderAndUser(String providerName, String userUuid);

    /**
     * Find an account by provider name and profile UUID (for bound accounts).
     * Returns null if not found.
     */
    ClientAccount findByProviderAndProfile(String providerName, UUID profileUuid);

    /**
     * Find an unbound account (profile_uuid IS NULL) by provider and user UUID.
     * Returns null if not found. Does not match rows that have a profile bound.
     */
    ClientAccount findUnboundByProviderAndUser(String providerName, String userUuid);

    /**
     * Find all accounts for a given provider, ordered by lastValidatedAt desc.
     */
    List<ClientAccount> findByProvider(String providerName);

    /**
     * Return all accounts across all providers, ordered by lastValidatedAt desc.
     */
    List<ClientAccount> listAll();

    /**
     * Persist a new account. Returns the generated row ID.
     */
    long create(ClientAccount account);

    /**
     * Update an existing account by id.
     */
    void update(ClientAccount account);

    /**
     * Delete an account by row ID.
     */
    void delete(long id);

    /**
     * Delete all accounts for a provider. Used when removing a provider.
     */
    void deleteByProvider(String providerName);

    /**
     * Return total account count.
     */
    long count();
}
