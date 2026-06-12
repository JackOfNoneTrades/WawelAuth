package org.fentanylsolutions.wawelauth.wawelnet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.util.HexUtil;
import org.fentanylsolutions.wawelauth.wawelcore.util.OwnerOnlyFileIO;

import cpw.mods.fml.common.StartupQuery;

/**
 * Provides the SSL context used by same-port HTTPS.
 * <p>
 * Netty 4.0.10 only ships {@link io.netty.handler.ssl.SslHandler}, not the
 * newer {@code SslContext} or {@code SelfSignedCertificate} helpers, so
 * certificate creation is delegated to Bouncy Castle and then fed into the JDK
 * TLS stack.
 */
public final class HttpsContextProvider {

    private static final String PRIVATE_KEY_FILE = "https-private.der";
    private static final String CERTIFICATE_FILE = "https-cert.der";
    private static final char[] KEY_PASSWORD = "wawelauth-local-https".toCharArray();
    private static final long CERT_VALIDITY_MS = 10L * 365L * 24L * 60L * 60L * 1000L;

    private static SSLContext cachedContext;
    private static String cachedFingerprint;

    private HttpsContextProvider() {}

    public static synchronized void prepareForStartup() {
        ensureInitialized();
    }

    public static synchronized SSLEngine newServerEngine() {
        ensureInitialized();
        SSLEngine engine = cachedContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledProtocols(filterSupportedProtocols(engine.getSupportedProtocols()));
        return engine;
    }

    private static void ensureInitialized() {
        try {
            if (cachedContext == null) {
                File stateDir = new File(Config.getConfigDir(), "data");
                LoadedCertificate loaded = loadOrGenerate(stateDir);
                cachedContext = buildContext(loaded.privateKey, loaded.certificate);
                cachedFingerprint = sha256Fingerprint(loaded.certificate.getEncoded());
                WawelAuth.LOG.info(
                    "Same-port HTTPS enabled with self-signed certificate fingerprint SHA-256 {}",
                    cachedFingerprint);
            }
        } catch (StartupQuery.AbortedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize same-port HTTPS", e);
        }
    }

    public static synchronized String getCertificateFingerprint() {
        if (cachedFingerprint == null) {
            newServerEngine();
        }
        return cachedFingerprint;
    }

    private static LoadedCertificate loadOrGenerate(File stateDir) throws Exception {
        if (!stateDir.exists() && !stateDir.mkdirs()) {
            throw new IOException("Failed to create state directory: " + stateDir);
        }

        File keyFile = new File(stateDir, PRIVATE_KEY_FILE);
        File certFile = new File(stateDir, CERTIFICATE_FILE);
        if (keyFile.exists() || certFile.exists()) {
            if (!keyFile.exists() || !certFile.exists()) {
                throw new IOException(
                    "Incomplete HTTPS certificate in " + stateDir
                        + ": delete both "
                        + PRIVATE_KEY_FILE
                        + " and "
                        + CERTIFICATE_FILE
                        + " to regenerate.");
            }
            PrivateKey privateKey = loadPrivateKey(keyFile);
            X509Certificate certificate = loadCertificate(certFile);
            OwnerOnlyFileIO.restrictToOwner(keyFile);
            List<SubjectAltName> requiredNames = collectSubjectAltNames();
            List<SubjectAltName> missingNames = missingSubjectAltNames(certificate, requiredNames);
            if (!missingNames.isEmpty()) {
                confirmCertificateRegeneration(stateDir, keyFile, certFile, certificate, requiredNames, missingNames);
                File backupDir = backupExistingCertificateFiles(stateDir, keyFile, certFile);
                certificate = createSelfSignedCertificate(privateKey, certificate.getPublicKey(), requiredNames);
                writeCertificateAtomically(certFile, certificate.getEncoded());
                WawelAuth.LOG.warn(
                    "Regenerated same-port HTTPS certificate with updated SANs. Previous certificate backup: {}",
                    backupDir.getAbsolutePath());
            }
            return new LoadedCertificate(privateKey, certificate);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        X509Certificate certificate = createSelfSignedCertificate(pair, collectSubjectAltNames());

        OwnerOnlyFileIO.writeNewOwnerOnly(
            keyFile,
            pair.getPrivate()
                .getEncoded());
        writeCertificateAtomically(certFile, certificate.getEncoded());
        WawelAuth.LOG.info("Generated same-port HTTPS certificate in {}", stateDir.getAbsolutePath());
        return new LoadedCertificate(pair.getPrivate(), certificate);
    }

    private static SSLContext buildContext(PrivateKey privateKey, X509Certificate certificate) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, KEY_PASSWORD);
        keyStore.setKeyEntry("wawelauth-https", privateKey, KEY_PASSWORD, new Certificate[] { certificate });

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEY_PASSWORD);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    private static PrivateKey loadPrivateKey(File file) throws Exception {
        byte[] der = Files.readAllBytes(file.toPath());
        return KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static X509Certificate loadCertificate(File file) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (ByteArrayInputStream in = new ByteArrayInputStream(Files.readAllBytes(file.toPath()))) {
            return (X509Certificate) factory.generateCertificate(in);
        }
    }

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, List<SubjectAltName> names)
        throws Exception {
        return createSelfSignedCertificate(keyPair.getPrivate(), keyPair.getPublic(), names);
    }

    private static X509Certificate createSelfSignedCertificate(PrivateKey privateKey, PublicKey publicKey,
        List<SubjectAltName> names) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 5L * 60L * 1000L);
        Date notAfter = new Date(now + CERT_VALIDITY_MS);
        BigInteger serial = new BigInteger(159, new SecureRandom()).abs();
        X500Name name = new X500Name("CN=WawelAuth Same-Port HTTPS");

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name,
            serial,
            notBefore,
            notAfter,
            name,
            publicKey);
        builder.addExtension(
            Extension.keyUsage,
            false,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        if (!names.isEmpty()) {
            GeneralName[] generalNames = new GeneralName[names.size()];
            for (int i = 0; i < names.size(); i++) {
                generalNames[i] = names.get(i)
                    .toGeneralName();
            }
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static List<SubjectAltName> collectSubjectAltNames() {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add("localhost");
        hosts.add("127.0.0.1");
        hosts.add("::1");

        ServerConfig config = Config.server();
        addHost(hosts, config.getPublicBaseUrl());
        addHost(hosts, config.getEffectiveApiRoot());
        addHost(hosts, config.getServerAddress());

        List<SubjectAltName> names = new ArrayList<>();
        for (String host : hosts) {
            SubjectAltName name = subjectAltName(host);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static SubjectAltName subjectAltName(String host) {
        if (host == null) return null;
        String trimmed = host.trim();
        if (trimmed.isEmpty()) return null;
        return new SubjectAltName(isIpLiteral(trimmed) ? GeneralName.iPAddress : GeneralName.dNSName, trimmed);
    }

    private static boolean isIpLiteral(String value) {
        return value.indexOf(':') >= 0 || value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }

    private static void addHost(Set<String> hosts, String value) {
        String host = extractHost(value);
        if (host != null) {
            hosts.add(host);
        }
    }

    private static String extractHost(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            URI uri = trimmed.contains("://") ? new URI(trimmed) : new URI("http://" + trimmed);
            String host = uri.getHost();
            if (host != null && !host.trim()
                .isEmpty()) {
                return host.trim();
            }
        } catch (Exception ignored) {}

        String host = trimmed;
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        if (host.startsWith("[") && host.contains("]")) {
            return host.substring(1, host.indexOf(']'));
        }
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        return host.isEmpty() ? null : host;
    }

    private static String[] filterSupportedProtocols(String[] supported) {
        List<String> allowed = new ArrayList<>();
        Collections.addAll(allowed, supported);
        List<String> selected = new ArrayList<>();
        if (allowed.contains("TLSv1.3")) selected.add("TLSv1.3");
        if (allowed.contains("TLSv1.2")) selected.add("TLSv1.2");
        return selected.isEmpty() ? supported : selected.toArray(new String[selected.size()]);
    }

    private static String sha256Fingerprint(byte[] data) throws GeneralSecurityException {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(data);
        String hex = HexUtil.bytesToHex(digest)
            .toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            if (out.length() > 0) {
                out.append(':');
            }
            out.append(hex, i, i + 2);
        }
        return out.toString();
    }

    private static List<SubjectAltName> missingSubjectAltNames(X509Certificate certificate,
        List<SubjectAltName> requiredNames) throws CertificateParsingException {
        List<SubjectAltName> missing = new ArrayList<>();
        Collection<List<?>> existingNames = certificate.getSubjectAlternativeNames();
        for (SubjectAltName required : requiredNames) {
            if (!containsSubjectAltName(existingNames, required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    private static boolean containsSubjectAltName(Collection<List<?>> existingNames, SubjectAltName required) {
        if (existingNames == null) {
            return false;
        }
        for (List<?> entry : existingNames) {
            if (entry == null || entry.size() < 2 || !(entry.get(0) instanceof Integer)) {
                continue;
            }
            int type = ((Integer) entry.get(0)).intValue();
            Object value = entry.get(1);
            if (value != null && required.matches(type, String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }

    private static void confirmCertificateRegeneration(File stateDir, File keyFile, File certFile,
        X509Certificate certificate, List<SubjectAltName> requiredNames, List<SubjectAltName> missingNames)
        throws GeneralSecurityException {
        String fingerprint = sha256Fingerprint(certificate.getEncoded());
        String message = "WawelAuth same-port HTTPS certificate does not cover the current configured hostnames.\n\n"
            + "Certificate: "
            + certFile.getAbsolutePath()
            + "\n"
            + "Private key: "
            + keyFile.getAbsolutePath()
            + "\n"
            + "Current certificate fingerprint SHA-256: "
            + fingerprint
            + "\n"
            + "Missing SAN entries: "
            + formatSubjectAltNames(missingNames)
            + "\n"
            + "Required SAN entries: "
            + formatSubjectAltNames(requiredNames)
            + "\n\n"
            + "Confirm to back up the existing HTTPS certificate files under:\n"
            + new File(stateDir, "https-certificate-backups").getAbsolutePath()
            + "\n"
            + "and regenerate the self-signed certificate using the existing HTTPS private key.\n"
            + "Cancel to abort server startup.";

        WawelAuth.LOG.error("============================================================");
        WawelAuth.LOG.error("WawelAuth same-port HTTPS certificate SANs are stale.");
        WawelAuth.LOG.error("Certificate: {}", certFile.getAbsolutePath());
        WawelAuth.LOG.error("Current certificate fingerprint SHA-256: {}", fingerprint);
        WawelAuth.LOG.error("Missing SAN entries: {}", formatSubjectAltNames(missingNames));
        WawelAuth.LOG.error("Required SAN entries: {}", formatSubjectAltNames(requiredNames));
        WawelAuth.LOG.error("Confirm the FML prompt to regenerate, or cancel to abort startup.");
        WawelAuth.LOG.error("============================================================");

        if (!confirmStartupQuery(message)) {
            WawelAuth.LOG.error("WawelAuth HTTPS certificate regeneration was cancelled; aborting server startup.");
            StartupQuery.abort();
        }
    }

    private static boolean confirmStartupQuery(String message) {
        String queryResult = System.getProperty("fml.queryResult");
        if (queryResult != null) {
            WawelAuth.LOG.info(
                "Using fml.queryResult={} to answer WawelAuth HTTPS certificate regeneration prompt.",
                queryResult);
            if ("confirm".equalsIgnoreCase(queryResult)) {
                return true;
            }
            if ("cancel".equalsIgnoreCase(queryResult)) {
                return false;
            }
            WawelAuth.LOG.warn("Invalid fml.queryResult value '{}'; expected confirm or cancel.", queryResult);
        }
        return StartupQuery.confirm(message);
    }

    private static File backupExistingCertificateFiles(File stateDir, File keyFile, File certFile) throws IOException {
        File backupRoot = new File(stateDir, "https-certificate-backups");
        Files.createDirectories(backupRoot.toPath());

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupDir = createUniqueBackupDir(backupRoot, timestamp);

        OwnerOnlyFileIO.writeNewOwnerOnly(new File(backupDir, PRIVATE_KEY_FILE), Files.readAllBytes(keyFile.toPath()));
        Files.write(new File(backupDir, CERTIFICATE_FILE).toPath(), Files.readAllBytes(certFile.toPath()));
        return backupDir;
    }

    private static File createUniqueBackupDir(File backupRoot, String timestamp) throws IOException {
        for (int i = 1; i <= 1000; i++) {
            String name = i == 1 ? timestamp : timestamp + "-" + i;
            File backupDir = new File(backupRoot, name);
            try {
                Files.createDirectory(backupDir.toPath());
                return backupDir;
            } catch (FileAlreadyExistsException e) {
                continue;
            }
        }
        throw new IOException("Failed to allocate unique HTTPS certificate backup directory under " + backupRoot);
    }

    private static void writeCertificateAtomically(File certFile, byte[] encoded) throws IOException {
        File parent = certFile.getParentFile();
        File tmpFile = File.createTempFile(certFile.getName(), ".tmp", parent);
        Files.write(tmpFile.toPath(), encoded);
        try {
            Files.move(
                tmpFile.toPath(),
                certFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile.toPath(), certFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile.toPath());
        }
    }

    private static String formatSubjectAltNames(List<SubjectAltName> names) {
        if (names == null || names.isEmpty()) {
            return "<none>";
        }
        StringBuilder out = new StringBuilder();
        for (SubjectAltName name : names) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(name.display());
        }
        return out.toString();
    }

    private static String normalizeSubjectAltNameValue(int type, String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (type == GeneralName.iPAddress) {
            try {
                return InetAddress.getByName(trimmed)
                    .getHostAddress()
                    .toLowerCase(Locale.ROOT);
            } catch (Exception ignored) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static final class SubjectAltName {

        final int type;
        final String value;
        final String normalizedValue;

        SubjectAltName(int type, String value) {
            this.type = type;
            this.value = value;
            this.normalizedValue = normalizeSubjectAltNameValue(type, value);
        }

        GeneralName toGeneralName() {
            return new GeneralName(type, value);
        }

        boolean matches(int otherType, String otherValue) {
            return type == otherType && normalizedValue.equals(normalizeSubjectAltNameValue(otherType, otherValue));
        }

        String display() {
            return (type == GeneralName.iPAddress ? "IP:" : "DNS:") + value;
        }
    }

    private static final class LoadedCertificate {

        final PrivateKey privateKey;
        final X509Certificate certificate;

        LoadedCertificate(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
