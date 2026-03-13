package org.fentanylsolutions.wawelauth.wawelcore.storage;

import java.util.Map;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.AdminPlayerListType;

/**
 * Stores which auth provider was used when an admin-added ops/whitelist entry
 * was resolved. This allows the web UI to fetch the correct head later.
 */
public interface AdminPlayerListProviderBindingDAO {

    Map<UUID, String> findAllProviderKeys(AdminPlayerListType listType);

    void putProviderKey(AdminPlayerListType listType, UUID profileUuid, String providerKey);

    void delete(AdminPlayerListType listType, UUID profileUuid);
}
