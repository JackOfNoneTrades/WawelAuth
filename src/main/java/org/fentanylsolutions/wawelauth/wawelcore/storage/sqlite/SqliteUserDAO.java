package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.WawelUser;
import org.fentanylsolutions.wawelauth.wawelcore.storage.UserDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase.SqlBinder;

public class SqliteUserDAO implements UserDAO {

    private final SqliteDatabase db;

    public SqliteUserDAO(SqliteDatabase db) {
        this.db = db;
    }

    private WawelUser mapRow(ResultSet rs) throws SQLException {
        WawelUser user = new WawelUser();
        user.setUuid(UUID.fromString(rs.getString("uuid")));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPasswordSalt(rs.getString("password_salt"));
        user.setAdmin(rs.getInt("admin") != 0);
        user.setLocked(rs.getInt("locked") != 0);
        user.setPreferredLanguage(rs.getString("preferred_language"));
        user.setCreatedAt(rs.getLong("created_at"));
        return user;
    }

    @Override
    public WawelUser findByUuid(UUID uuid) {
        return db.queryOne("SELECT * FROM users WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()), this::mapRow);
    }

    @Override
    public WawelUser findByUsername(String username) {
        return db.queryOne(
            "SELECT * FROM users WHERE username = ? COLLATE NOCASE",
            ps -> ps.setString(1, username),
            this::mapRow);
    }

    @Override
    public void create(WawelUser user) {
        db.executeUpdate(
            "INSERT INTO users (uuid, username, password_hash, password_salt, admin, locked, preferred_language, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                ps.setString(
                    1,
                    user.getUuid()
                        .toString());
                ps.setString(2, user.getUsername());
                ps.setString(3, user.getPasswordHash());
                ps.setString(4, user.getPasswordSalt());
                ps.setInt(5, user.isAdmin() ? 1 : 0);
                ps.setInt(6, user.isLocked() ? 1 : 0);
                ps.setString(7, user.getPreferredLanguage());
                ps.setLong(8, user.getCreatedAt());
            });
    }

    @Override
    public void update(WawelUser user) {
        int rows = db.executeUpdate(
            "UPDATE users SET username = ?, password_hash = ?, password_salt = ?, admin = ?, locked = ?, preferred_language = ? WHERE uuid = ?",
            ps -> {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPasswordHash());
                ps.setString(3, user.getPasswordSalt());
                ps.setInt(4, user.isAdmin() ? 1 : 0);
                ps.setInt(5, user.isLocked() ? 1 : 0);
                ps.setString(6, user.getPreferredLanguage());
                ps.setString(
                    7,
                    user.getUuid()
                        .toString());
            });
        if (rows == 0) throw new RuntimeException("User not found: " + user.getUuid());
    }

    @Override
    public void delete(UUID uuid) {
        db.executeUpdate("DELETE FROM users WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()));
    }

    @Override
    public List<WawelUser> listAll() {
        return db.queryList("SELECT * FROM users ORDER BY created_at", SqlBinder.NONE, this::mapRow);
    }

    @Override
    public long count() {
        return db.queryOne("SELECT COUNT(*) FROM users", SqlBinder.NONE, rs -> rs.getLong(1));
    }
}
