package org.fentanylsolutions.wawelauth.wawelcore.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import org.fentanylsolutions.wawelauth.WawelAuth;

public final class OwnerOnlyFileIO {

    private static final Set<PosixFilePermission> OWNER_READ_WRITE = EnumSet
        .of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private OwnerOnlyFileIO() {}

    public static void writeNewOwnerOnly(File file, byte[] data) throws IOException {
        Path path = file.toPath();
        boolean created = false;
        try {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
            created = true;
        } catch (UnsupportedOperationException e) {
            if (!file.createNewFile()) {
                throw new FileAlreadyExistsException(file.getAbsolutePath());
            }
            created = true;
            restrictToOwner(file);
        }

        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.WRITE)) {
            out.write(data);
        } catch (IOException | RuntimeException e) {
            if (created) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            }
            throw e;
        }

        restrictToOwner(file);
    }

    /**
     * Set file permissions to owner-only read/write (0600). Falls back to the
     * old File API on non-POSIX filesystems.
     */
    public static void restrictToOwner(File file) {
        try {
            Files.setPosixFilePermissions(file.toPath(), OWNER_READ_WRITE);
        } catch (UnsupportedOperationException e) {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to restrict permissions on {}", file.getAbsolutePath(), e);
        }
    }
}
