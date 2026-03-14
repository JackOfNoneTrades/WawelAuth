package org.fentanylsolutions.wawelauth.wawelclient.storage;

import java.util.List;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

/**
 * Data access interface for {@link ClientProvider} entities.
 * <p>
 * Manages the stored Yggdrasil-compatible authentication providers
 * (built-in Mojang + user-added custom providers).
 */
public interface ClientProviderDAO {

    /**
     * Find a provider by name. Returns null if not found.
     */
    ClientProvider findByName(String name);

    /**
     * Return all providers, ordered by createdAt.
     */
    List<ClientProvider> listAll();

    /**
     * Persist a new provider. Throws if name already exists.
     */
    void create(ClientProvider provider);

    /**
     * Update an existing provider by name.
     */
    void update(ClientProvider provider);

    /**
     * Rename a provider and migrate all dependent accounts to the new name.
     *
     * @throws IllegalArgumentException if old provider is missing or new name already exists
     */
    void rename(String oldName, String newName);

    /**
     * Delete a provider by name. Accounts referencing this provider are cascade-deleted.
     */
    void delete(String name);

    /**
     * Return total provider count.
     */
    long count();
}
