package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.WawelInvite;
import org.fentanylsolutions.wawelauth.wawelcore.storage.InviteDAO;

public class SqliteInviteDAO implements InviteDAO {

    private final SqliteDatabase db;

    public SqliteInviteDAO(SqliteDatabase db) {
        this.db = db;
    }

    private WawelInvite mapRow(ResultSet rs) throws SQLException {
        WawelInvite invite = new WawelInvite();
        invite.setCode(rs.getString("code"));
        invite.setCreatedAt(rs.getLong("created_at"));
        String createdBy = rs.getString("created_by");
        if (createdBy != null) invite.setCreatedBy(UUID.fromString(createdBy));
        invite.setUsesRemaining(rs.getInt("uses_remaining"));
        return invite;
    }

    @Override
    public WawelInvite findByCode(String code) {
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM invites WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public void create(WawelInvite invite) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO invites (code, created_at, created_by, uses_remaining) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, invite.getCode());
                ps.setLong(2, invite.getCreatedAt());
                ps.setString(
                    3,
                    invite.getCreatedBy() != null ? invite.getCreatedBy()
                        .toString() : null);
                ps.setInt(4, invite.getUsesRemaining());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public boolean consume(String code) {
        // Atomic under the database lock: decrement uses_remaining only if > 0,
        // or leave alone if -1 (unlimited). If the invite reaches 0, delete it
        // immediately so exhausted tokens do not linger in admin listings.
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE invites SET uses_remaining = CASE " + "WHEN uses_remaining = -1 THEN -1 "
                    + "ELSE uses_remaining - 1 "
                    + "END "
                    + "WHERE code = ? AND (uses_remaining > 0 OR uses_remaining = -1)")) {
                ps.setString(1, code);
                boolean consumed = ps.executeUpdate() > 0;
                if (!consumed) {
                    return false;
                }
            }

            try (PreparedStatement cleanup = conn
                .prepareStatement("DELETE FROM invites WHERE code = ? AND uses_remaining = 0")) {
                cleanup.setString(1, code);
                cleanup.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public void delete(String code) {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM invites WHERE code = ?")) {
                ps.setString(1, code);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<WawelInvite> listAll() {
        return db.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM invites ORDER BY created_at");
                ResultSet rs = ps.executeQuery()) {
                List<WawelInvite> invites = new ArrayList<>();
                while (rs.next()) {
                    invites.add(mapRow(rs));
                }
                return invites;
            }
        });
    }

    @Override
    public void purgeConsumed() {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM invites WHERE uses_remaining = 0")) {
                ps.executeUpdate();
            }
        });
    }
}
