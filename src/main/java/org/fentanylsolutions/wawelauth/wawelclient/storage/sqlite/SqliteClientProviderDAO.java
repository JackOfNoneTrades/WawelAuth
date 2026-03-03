package org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.EnumUtil;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase;

public class SqliteClientProviderDAO implements ClientProviderDAO {

    private final SqliteDatabase db;

    public SqliteClientProviderDAO(SqliteDatabase db) {
        this.db = db;
    }

    private ClientProvider mapRow(ResultSet rs) throws SQLException {
        ClientProvider p = new ClientProvider();
        p.setName(rs.getString("name"));
        p.setType(EnumUtil.parseOrDefault(ProviderType.class, rs.getString("type"), ProviderType.CUSTOM));
        p.setApiRoot(rs.getString("api_root"));
        p.setAuthServerUrl(rs.getString("auth_server_url"));
        p.setSessionServerUrl(rs.getString("session_server_url"));
        p.setServicesUrl(rs.getString("services_url"));
        p.setSkinDomains(rs.getString("skin_domains"));
        p.setPublicKeyBase64(rs.getString("public_key"));
        p.setPublicKeyFingerprint(rs.getString("public_key_fingerprint"));
        p.setCreatedAt(rs.getLong("created_at"));
        p.setManualEntry(rs.getInt("manual_added") != 0);
        return p;
    }

    @Override
    public ClientProvider findByName(String name) {
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM providers WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public List<ClientProvider> listAll() {
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM providers ORDER BY created_at")) {
                try (ResultSet rs = ps.executeQuery()) {
                    List<ClientProvider> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            }
        });
    }

    @Override
    public void create(ClientProvider p) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO providers (name, type, api_root, auth_server_url, session_server_url, "
                    + "services_url, skin_domains, public_key, public_key_fingerprint, created_at, manual_added) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, p.getName());
                ps.setString(
                    2,
                    p.getType()
                        .name());
                ps.setString(3, p.getApiRoot());
                ps.setString(4, p.getAuthServerUrl());
                ps.setString(5, p.getSessionServerUrl());
                ps.setString(6, p.getServicesUrl());
                ps.setString(7, p.getSkinDomains());
                ps.setString(8, p.getPublicKeyBase64());
                ps.setString(9, p.getPublicKeyFingerprint());
                ps.setLong(10, p.getCreatedAt());
                ps.setInt(11, p.isManualEntry() ? 1 : 0);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void update(ClientProvider p) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE providers SET type = ?, api_root = ?, auth_server_url = ?, session_server_url = ?, "
                    + "services_url = ?, skin_domains = ?, public_key = ?, public_key_fingerprint = ?, "
                    + "created_at = ?, manual_added = ? WHERE name = ?")) {
                ps.setString(
                    1,
                    p.getType()
                        .name());
                ps.setString(2, p.getApiRoot());
                ps.setString(3, p.getAuthServerUrl());
                ps.setString(4, p.getSessionServerUrl());
                ps.setString(5, p.getServicesUrl());
                ps.setString(6, p.getSkinDomains());
                ps.setString(7, p.getPublicKeyBase64());
                ps.setString(8, p.getPublicKeyFingerprint());
                ps.setLong(9, p.getCreatedAt());
                ps.setInt(10, p.isManualEntry() ? 1 : 0);
                ps.setString(11, p.getName());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void rename(String oldName, String newName) {
        if (oldName == null || newName == null) {
            throw new IllegalArgumentException("Provider names must not be null");
        }
        if (oldName.equals(newName)) {
            return;
        }

        db.runInTransaction(() -> {
            ClientProvider old = findByName(oldName);
            if (old == null) {
                throw new IllegalArgumentException("Provider not found: " + oldName);
            }
            if (findByName(newName) != null) {
                throw new IllegalArgumentException("Provider name already taken: " + newName);
            }

            db.execute(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO providers (name, type, api_root, auth_server_url, session_server_url, "
                        + "services_url, skin_domains, public_key, public_key_fingerprint, created_at, manual_added) "
                        + "SELECT ?, type, api_root, auth_server_url, session_server_url, "
                        + "services_url, skin_domains, public_key, public_key_fingerprint, created_at, manual_added "
                        + "FROM providers WHERE name = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    int inserted = ps.executeUpdate();
                    if (inserted == 0) {
                        throw new RuntimeException("Failed to create renamed provider row for: " + oldName);
                    }
                }
            });

            db.execute(conn -> {
                try (PreparedStatement ps = conn
                    .prepareStatement("UPDATE accounts SET provider_name = ? WHERE provider_name = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
            });

            db.execute(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM providers WHERE name = ?")) {
                    ps.setString(1, oldName);
                    int deleted = ps.executeUpdate();
                    if (deleted == 0) {
                        throw new RuntimeException("Failed to delete old provider row for: " + oldName);
                    }
                }
            });
        });
    }

    @Override
    public void delete(String name) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM providers WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public long count() {
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM providers");
                ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        });
    }
}
