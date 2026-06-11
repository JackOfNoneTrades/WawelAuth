package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.StoredTexture;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TextureDAO;

public class SqliteTextureDAO implements TextureDAO {

    private final SqliteDatabase db;

    public SqliteTextureDAO(SqliteDatabase db) {
        this.db = db;
    }

    private StoredTexture mapRow(ResultSet rs) throws SQLException {
        StoredTexture tex = new StoredTexture();
        tex.setHash(rs.getString("hash"));
        tex.setTextureType(EnumUtil.parseOrDefault(TextureType.class, rs.getString("texture_type"), TextureType.SKIN));
        String uploadedBy = rs.getString("uploaded_by");
        if (uploadedBy != null) tex.setUploadedBy(UUID.fromString(uploadedBy));
        tex.setUploadedAt(rs.getLong("uploaded_at"));
        tex.setContentLength(rs.getLong("content_length"));
        tex.setWidth(rs.getInt("width"));
        tex.setHeight(rs.getInt("height"));
        return tex;
    }

    @Override
    public StoredTexture findByHash(String hash) {
        return db.queryOne("SELECT * FROM stored_textures WHERE hash = ?", ps -> ps.setString(1, hash), this::mapRow);
    }

    @Override
    public void create(StoredTexture texture) {
        // INSERT OR IGNORE: content-addressed dedup: if hash exists, skip silently.
        db.executeUpdate(
            "INSERT OR IGNORE INTO stored_textures (hash, texture_type, uploaded_by, uploaded_at, content_length, width, height) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                ps.setString(1, texture.getHash());
                ps.setString(
                    2,
                    texture.getTextureType()
                        .name());
                ps.setString(
                    3,
                    texture.getUploadedBy() != null ? texture.getUploadedBy()
                        .toString() : null);
                ps.setLong(4, texture.getUploadedAt());
                ps.setLong(5, texture.getContentLength());
                ps.setInt(6, texture.getWidth());
                ps.setInt(7, texture.getHeight());
            });
    }

    @Override
    public void delete(String hash) {
        db.executeUpdate("DELETE FROM stored_textures WHERE hash = ?", ps -> ps.setString(1, hash));
    }

    @Override
    public boolean isReferenced(String hash) {
        return Boolean.TRUE.equals(
            db.queryOne(
                "SELECT EXISTS(" + "SELECT 1 FROM profiles WHERE skin_hash = ? OR cape_hash = ? OR elytra_hash = ?"
                    + ")",
                ps -> {
                    ps.setString(1, hash);
                    ps.setString(2, hash);
                    ps.setString(3, hash);
                },
                rs -> rs.getInt(1) != 0));
    }
}
