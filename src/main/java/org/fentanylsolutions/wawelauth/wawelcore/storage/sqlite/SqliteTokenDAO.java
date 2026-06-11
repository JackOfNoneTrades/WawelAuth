package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.TokenState;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelToken;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TokenDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase.SqlBinder;

public class SqliteTokenDAO implements TokenDAO {

    private final SqliteDatabase db;

    public SqliteTokenDAO(SqliteDatabase db) {
        this.db = db;
    }

    private WawelToken mapRow(ResultSet rs) throws SQLException {
        WawelToken token = new WawelToken();
        token.setAccessToken(rs.getString("access_token"));
        token.setClientToken(rs.getString("client_token"));
        token.setUserUuid(UUID.fromString(rs.getString("user_uuid")));
        String profileUuid = rs.getString("profile_uuid");
        if (profileUuid != null) token.setProfileUuid(UUID.fromString(profileUuid));
        token.setIssuedAt(rs.getLong("issued_at"));
        token.setLastUsedAt(rs.getLong("last_used_at"));
        token.setVersion(rs.getInt("version"));
        token.setState(EnumUtil.parseOrDefault(TokenState.class, rs.getString("state"), TokenState.INVALID));
        return token;
    }

    @Override
    public WawelToken findByAccessToken(String accessToken) {
        return db
            .queryOne("SELECT * FROM tokens WHERE access_token = ?", ps -> ps.setString(1, accessToken), this::mapRow);
    }

    @Override
    public WawelToken findByTokenPair(String accessToken, String clientToken) {
        if (clientToken == null) {
            return findByAccessToken(accessToken);
        }
        return db.queryOne("SELECT * FROM tokens WHERE access_token = ? AND client_token = ?", ps -> {
            ps.setString(1, accessToken);
            ps.setString(2, clientToken);
        }, this::mapRow);
    }

    @Override
    public List<WawelToken> findByUser(UUID userUuid) {
        return db.queryList(
            "SELECT * FROM tokens WHERE user_uuid = ? ORDER BY issued_at DESC",
            ps -> ps.setString(1, userUuid.toString()),
            this::mapRow);
    }

    @Override
    public long countByUser(UUID userUuid) {
        return db.queryOne(
            "SELECT COUNT(*) FROM tokens WHERE user_uuid = ?",
            ps -> ps.setString(1, userUuid.toString()),
            rs -> rs.getLong(1));
    }

    @Override
    public void create(WawelToken token) {
        db.executeUpdate(
            "INSERT INTO tokens (access_token, client_token, user_uuid, profile_uuid, issued_at, last_used_at, version, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                ps.setString(1, token.getAccessToken());
                ps.setString(2, token.getClientToken());
                ps.setString(
                    3,
                    token.getUserUuid()
                        .toString());
                ps.setString(
                    4,
                    token.getProfileUuid() != null ? token.getProfileUuid()
                        .toString() : null);
                ps.setLong(5, token.getIssuedAt());
                ps.setLong(6, token.getLastUsedAt());
                ps.setInt(7, token.getVersion());
                ps.setString(
                    8,
                    token.getState()
                        .name());
            });
    }

    @Override
    public void update(WawelToken token) {
        int rows = db.executeUpdate(
            "UPDATE tokens SET client_token = ?, user_uuid = ?, profile_uuid = ?, issued_at = ?, last_used_at = ?, version = ?, state = ? WHERE access_token = ?",
            ps -> {
                ps.setString(1, token.getClientToken());
                ps.setString(
                    2,
                    token.getUserUuid()
                        .toString());
                ps.setString(
                    3,
                    token.getProfileUuid() != null ? token.getProfileUuid()
                        .toString() : null);
                ps.setLong(4, token.getIssuedAt());
                ps.setLong(5, token.getLastUsedAt());
                ps.setInt(6, token.getVersion());
                ps.setString(
                    7,
                    token.getState()
                        .name());
                ps.setString(8, token.getAccessToken());
            });
        if (rows == 0) throw new RuntimeException("Token not found: " + token.getAccessToken());
    }

    @Override
    public void delete(String accessToken) {
        db.executeUpdate("DELETE FROM tokens WHERE access_token = ?", ps -> ps.setString(1, accessToken));
    }

    @Override
    public void deleteByUser(UUID userUuid) {
        db.executeUpdate("DELETE FROM tokens WHERE user_uuid = ?", ps -> ps.setString(1, userUuid.toString()));
    }

    @Override
    public void markTemporarilyInvalid(UUID profileUuid) {
        db.executeUpdate("UPDATE tokens SET state = ? WHERE profile_uuid = ? AND state = ?", ps -> {
            ps.setString(1, TokenState.TEMPORARILY_INVALID.name());
            ps.setString(2, profileUuid.toString());
            ps.setString(3, TokenState.VALID.name());
        });
    }

    @Override
    public void evictOldest(UUID userUuid, int keepCount) {
        db.executeUpdate(
            "DELETE FROM tokens WHERE user_uuid = ? AND access_token NOT IN ("
                + "SELECT access_token FROM tokens WHERE user_uuid = ? ORDER BY issued_at DESC, rowid DESC LIMIT ?)",
            ps -> {
                ps.setString(1, userUuid.toString());
                ps.setString(2, userUuid.toString());
                ps.setInt(3, keepCount);
            });
    }

    @Override
    public void purgeInvalid() {
        db.executeUpdate("DELETE FROM tokens WHERE state = ?", ps -> ps.setString(1, TokenState.INVALID.name()));
    }

    @Override
    public long count() {
        return db.queryOne("SELECT COUNT(*) FROM tokens", SqlBinder.NONE, rs -> rs.getLong(1));
    }
}
