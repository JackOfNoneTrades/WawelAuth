package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelcore.storage.UserListProviderBindingDAO;

public class SqliteUserListProviderBindingDAO implements UserListProviderBindingDAO {

    private final SqliteDatabase db;

    public SqliteUserListProviderBindingDAO(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public Map<UUID, String> findAllProviderKeys(ProviderAwareUserListType listType) {
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT profile_uuid, provider_key FROM user_list_provider_bindings WHERE list_type = ?")) {
                ps.setString(1, listType.name());
                try (ResultSet rs = ps.executeQuery()) {
                    Map<UUID, String> out = new LinkedHashMap<>();
                    while (rs.next()) {
                        String uuidRaw = rs.getString("profile_uuid");
                        String providerKey = rs.getString("provider_key");
                        if (uuidRaw == null || providerKey == null) {
                            continue;
                        }
                        try {
                            out.put(UUID.fromString(uuidRaw), providerKey);
                        } catch (Exception ignored) {}
                    }
                    return out;
                }
            }
        });
    }

    @Override
    public void putProviderKey(ProviderAwareUserListType listType, UUID profileUuid, String providerKey) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO user_list_provider_bindings (list_type, profile_uuid, provider_key, updated_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, listType.name());
                ps.setString(2, profileUuid.toString());
                ps.setString(3, providerKey);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void delete(ProviderAwareUserListType listType, UUID profileUuid) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn
                .prepareStatement("DELETE FROM user_list_provider_bindings WHERE list_type = ? AND profile_uuid = ?")) {
                ps.setString(1, listType.name());
                ps.setString(2, profileUuid.toString());
                ps.executeUpdate();
            }
        });
    }
}
