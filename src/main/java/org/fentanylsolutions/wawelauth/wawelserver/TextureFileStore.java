package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.fentanylsolutions.wawelauth.WawelAuth;

/**
 * File I/O for texture images stored as {@code {stateDir}/textures/{hash}.png}
 * or {@code {hash}.gif}.
 * <p>
 * Content-addressed: the filename is the SHA-256 hash of the file contents.
 */
public class TextureFileStore {

    private final File textureDir;

    public TextureFileStore(File stateDir) {
        this.textureDir = new File(stateDir, "textures");
        if (!textureDir.exists() && !textureDir.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create texture directory: {}", textureDir.getAbsolutePath());
        }
        deleteLeftoverTempFiles();
    }

    private void deleteLeftoverTempFiles() {
        File[] files = textureDir.listFiles((dir, name) -> name.endsWith(".tmp"));
        if (files == null) return;
        for (File file : files) {
            if (!file.delete()) {
                WawelAuth.LOG.warn("Failed to delete leftover temp texture file: {}", file.getName());
            }
        }
    }

    public byte[] read(String hash) {
        File file = pngFileFor(hash);
        if (!file.exists()) {
            file = gifFileFor(hash);
        }
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return data;
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to read texture {}: {}", hash, e.getMessage());
            return null;
        }
    }

    public void write(String hash, byte[] data) throws IOException {
        File file = pngFileFor(hash);
        if (file.exists()) {
            return; // content-addressed dedup
        }
        writeAtomically(file, data);
    }

    public void writeGif(String hash, byte[] data) throws IOException {
        File file = gifFileFor(hash);
        if (file.exists()) {
            return; // content-addressed dedup
        }
        writeAtomically(file, data);
    }

    /**
     * Writes to a unique temp file and renames it into place, so a crash
     * mid-write cannot persist a corrupt file under a valid hash name
     * (the dedup check would then block the correct content forever).
     */
    private void writeAtomically(File destination, byte[] data) throws IOException {
        File tmp = File.createTempFile(destination.getName() + ".", ".tmp", textureDir);
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
                fos.getFD()
                    .sync();
            }
            try {
                Files.move(
                    tmp.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tmp.exists() && !tmp.delete()) {
                WawelAuth.LOG.warn("Failed to delete temp texture file: {}", tmp.getName());
            }
        }
    }

    public void delete(String hash) {
        File png = pngFileFor(hash);
        if (png.exists()) {
            if (!png.delete()) {
                WawelAuth.LOG.warn("Failed to delete texture file: {}.png", hash);
            }
            return;
        }
        File gif = gifFileFor(hash);
        if (gif.exists() && !gif.delete()) {
            WawelAuth.LOG.warn("Failed to delete texture file: {}.gif", hash);
        }
    }

    /**
     * Returns the hashes of all stored texture files.
     */
    public java.util.Set<String> listStoredHashes() {
        java.util.Set<String> hashes = new java.util.HashSet<>();
        File[] files = textureDir.listFiles();
        if (files == null) {
            return hashes;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".png") || name.endsWith(".gif")) {
                hashes.add(name.substring(0, name.length() - 4));
            }
        }
        return hashes;
    }

    public boolean exists(String hash) {
        return pngFileFor(hash).exists() || gifFileFor(hash).exists();
    }

    public boolean isGif(String hash) {
        return gifFileFor(hash).exists();
    }

    private File pngFileFor(String hash) {
        return new File(textureDir, hash + ".png");
    }

    private File gifFileFor(String hash) {
        return new File(textureDir, hash + ".gif");
    }
}
