package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase.SqlBinder;

public class SqliteProfileDAO implements ProfileDAO {

    private final SqliteDatabase db;

    public SqliteProfileDAO(SqliteDatabase db) {
        this.db = db;
    }

    private WawelProfile mapRow(ResultSet rs) throws SQLException {
        WawelProfile profile = new WawelProfile();
        profile.setUuid(UUID.fromString(rs.getString("uuid")));
        profile.setName(rs.getString("name"));
        profile.setOwnerUuid(UUID.fromString(rs.getString("owner_uuid")));
        String offlineUuid = rs.getString("offline_uuid");
        if (offlineUuid != null) profile.setOfflineUuid(UUID.fromString(offlineUuid));
        profile.setSkinModel(EnumUtil.parseOrDefault(SkinModel.class, rs.getString("skin_model"), SkinModel.CLASSIC));
        profile.setSkinHash(rs.getString("skin_hash"));
        profile.setCapeHash(rs.getString("cape_hash"));
        profile.setElytraHash(rs.getString("elytra_hash"));
        profile.setUploadableTextures(deserializeUploadable(rs.getString("uploadable_textures")));
        profile.setCapeAnimated(rs.getInt("cape_animated") != 0);
        profile.setCreatedAt(rs.getLong("created_at"));
        return profile;
    }

    static String serializeUploadable(Set<TextureType> types) {
        if (types == null) return null;
        StringBuilder sb = new StringBuilder();
        for (TextureType t : types) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t.name());
        }
        return sb.toString();
    }

    static Set<TextureType> deserializeUploadable(String value) {
        if (value == null) return null;
        if (value.isEmpty()) return EnumSet.noneOf(TextureType.class);
        EnumSet<TextureType> set = EnumSet.noneOf(TextureType.class);
        for (String part : value.split(",")) {
            try {
                set.add(TextureType.valueOf(part.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    @Override
    public WawelProfile findByUuid(UUID uuid) {
        return db
            .queryOne("SELECT * FROM profiles WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()), this::mapRow);
    }

    @Override
    public WawelProfile findByName(String name) {
        return db.queryOne(
            "SELECT * FROM profiles WHERE name = ? COLLATE NOCASE",
            ps -> ps.setString(1, name),
            this::mapRow);
    }

    @Override
    public List<WawelProfile> findByOwner(UUID userUuid) {
        return db.queryList(
            "SELECT * FROM profiles WHERE owner_uuid = ? ORDER BY created_at",
            ps -> ps.setString(1, userUuid.toString()),
            this::mapRow);
    }

    @Override
    public List<WawelProfile> findByNames(List<String> names) {
        if (names == null || names.isEmpty()) return new ArrayList<>();
        // Build parameterized IN clause
        StringBuilder sql = new StringBuilder("SELECT * FROM profiles WHERE name COLLATE NOCASE IN (");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');
        return db.queryList(sql.toString(), ps -> {
            for (int i = 0; i < names.size(); i++) {
                ps.setString(i + 1, names.get(i));
            }
        }, this::mapRow);
    }

    @Override
    public void create(WawelProfile profile) {
        db.executeUpdate(
            "INSERT INTO profiles (uuid, name, owner_uuid, offline_uuid, skin_model, skin_hash, cape_hash, elytra_hash, uploadable_textures, cape_animated, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                ps.setString(
                    1,
                    profile.getUuid()
                        .toString());
                ps.setString(2, profile.getName());
                ps.setString(
                    3,
                    profile.getOwnerUuid()
                        .toString());
                ps.setString(
                    4,
                    profile.getOfflineUuid() != null ? profile.getOfflineUuid()
                        .toString() : null);
                ps.setString(
                    5,
                    profile.getSkinModel()
                        .name());
                ps.setString(6, profile.getSkinHash());
                ps.setString(7, profile.getCapeHash());
                ps.setString(8, profile.getElytraHash());
                ps.setString(9, serializeUploadable(profile.getUploadableTextures()));
                ps.setInt(10, profile.isCapeAnimated() ? 1 : 0);
                ps.setLong(11, profile.getCreatedAt());
            });
    }

    @Override
    public void update(WawelProfile profile) {
        int rows = db.executeUpdate(
            "UPDATE profiles SET name = ?, owner_uuid = ?, offline_uuid = ?, skin_model = ?, skin_hash = ?, cape_hash = ?, elytra_hash = ?, uploadable_textures = ?, cape_animated = ? WHERE uuid = ?",
            ps -> {
                ps.setString(1, profile.getName());
                ps.setString(
                    2,
                    profile.getOwnerUuid()
                        .toString());
                ps.setString(
                    3,
                    profile.getOfflineUuid() != null ? profile.getOfflineUuid()
                        .toString() : null);
                ps.setString(
                    4,
                    profile.getSkinModel()
                        .name());
                ps.setString(5, profile.getSkinHash());
                ps.setString(6, profile.getCapeHash());
                ps.setString(7, profile.getElytraHash());
                ps.setString(8, serializeUploadable(profile.getUploadableTextures()));
                ps.setInt(9, profile.isCapeAnimated() ? 1 : 0);
                ps.setString(
                    10,
                    profile.getUuid()
                        .toString());
            });
        if (rows == 0) throw new RuntimeException("Profile not found: " + profile.getUuid());
    }

    @Override
    public void delete(UUID uuid) {
        db.executeUpdate("DELETE FROM profiles WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()));
    }

    @Override
    public boolean isTextureHashReferenced(String hash) {
        if (hash == null) return false;
        return Boolean.TRUE.equals(
            db.queryOne(
                "SELECT 1 FROM profiles WHERE skin_hash = ? OR cape_hash = ? OR elytra_hash = ? LIMIT 1",
                ps -> {
                    ps.setString(1, hash);
                    ps.setString(2, hash);
                    ps.setString(3, hash);
                },
                rs -> true));
    }

    @Override
    public long count() {
        return db.queryOne("SELECT COUNT(*) FROM profiles", SqlBinder.NONE, rs -> rs.getLong(1));
    }
}
