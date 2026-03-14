package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    public void writeGif(String hash, byte[] data) throws IOException {
        File file = gifFileFor(hash);
        if (file.exists()) {
            return; // content-addressed dedup
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
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
