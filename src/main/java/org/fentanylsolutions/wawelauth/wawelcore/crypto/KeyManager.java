package org.fentanylsolutions.wawelauth.wawelcore.crypto;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;

import org.fentanylsolutions.wawelauth.WawelAuth;

/**
 * Manages the server's RSA keypair for signing profile properties.
 * <p>
 * The keypair is generated on first run and stored as DER-encoded files
 * in the state directory. The private key file is restricted to owner-only
 * permissions (0600) on POSIX systems. The public key is served via the
 * API metadata endpoint as base64-encoded DER (per Yggdrasil spec).
 */
public class KeyManager {

    private static final String PRIVATE_KEY_FILE = "private.der";
    private static final String PUBLIC_KEY_FILE = "public.der";
    private static final int KEY_SIZE = 4096;

    private final File stateDir;
    private KeyPair keyPair;

    public KeyManager(File stateDir) {
        this.stateDir = stateDir;
    }

    /**
     * Load the keypair from disk, or generate and save a new one if no keys exist yet.
     * Fails fast if existing key files are present but unreadable/corrupt.
     */
    public void loadOrGenerate() {
        File privFile = new File(stateDir, PRIVATE_KEY_FILE);
        File pubFile = new File(stateDir, PUBLIC_KEY_FILE);

        if (privFile.exists() || pubFile.exists()) {
            // Keys exist (or partially exist): load them, fail-fast on error.
            if (!privFile.exists() || !pubFile.exists()) {
                throw new RuntimeException(
                    "Incomplete keypair in " + stateDir
                        + ": one key file is missing. Remove both files to regenerate.");
            }
            try {
                PrivateKey priv = loadPrivateKey(privFile);
                PublicKey pub = loadPublicKey(pubFile);
                keyPair = new KeyPair(pub, priv);
                restrictToOwner(privFile);
                WawelAuth.LOG.info("Loaded RSA keypair from {}", stateDir.getAbsolutePath());
            } catch (Exception e) {
                throw new RuntimeException(
                    "Failed to load keypair from " + stateDir + ": delete both key files to regenerate",
                    e);
            }
            return;
        }

        // No keys yet: generate fresh.
        keyPair = generateKeyPair();
        if (!stateDir.exists() && !stateDir.mkdirs()) {
            throw new RuntimeException("Failed to create state directory: " + stateDir);
        }
        try {
            saveKey(
                pubFile,
                keyPair.getPublic()
                    .getEncoded(),
                false);
            saveKey(
                privFile,
                keyPair.getPrivate()
                    .getEncoded(),
                true);
            WawelAuth.LOG.info("Generated new RSA-{} keypair in {}", KEY_SIZE, stateDir.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save keypair", e);
        }
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    /**
     * Returns the public key as base64-encoded DER, for the API metadata endpoint.
     */
    public String getPublicKeyBase64() {
        return Base64.getEncoder()
            .encodeToString(
                keyPair.getPublic()
                    .getEncoded());
    }

    /**
     * Returns the raw DER-encoded public key bytes.
     */
    public byte[] getPublicKeyDer() {
        return keyPair.getPublic()
            .getEncoded();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(KEY_SIZE);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA not available", e);
        }
    }

    private static PrivateKey loadPrivateKey(File file)
        throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] der = Files.readAllBytes(file.toPath());
        return KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey loadPublicKey(File file)
        throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] der = Files.readAllBytes(file.toPath());
        return KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(der));
    }

    private static void saveKey(File file, byte[] encoded, boolean restrictPermissions) throws IOException {
        Files.write(file.toPath(), encoded);
        if (restrictPermissions) {
            restrictToOwner(file);
        }
    }

    /**
     * Set file permissions to owner-only read/write (0600). No-op on non-POSIX systems.
     */
    private static void restrictToOwner(File file) {
        try {
            Files.setPosixFilePermissions(
                file.toPath(),
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            // Non-POSIX filesystem (Windows): best effort via File API
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to restrict permissions on {}", file.getAbsolutePath(), e);
        }
    }
}
