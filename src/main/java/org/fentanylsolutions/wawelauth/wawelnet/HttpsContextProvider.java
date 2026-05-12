package org.fentanylsolutions.wawelauth.wawelnet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
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

    static synchronized SSLEngine newServerEngine() {
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

            SSLEngine engine = cachedContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setEnabledProtocols(filterSupportedProtocols(engine.getSupportedProtocols()));
            return engine;
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
            restrictToOwner(keyFile);
            return new LoadedCertificate(privateKey, certificate);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        X509Certificate certificate = createSelfSignedCertificate(pair, collectSubjectAltNames());

        Files.write(
            keyFile.toPath(),
            pair.getPrivate()
                .getEncoded());
        restrictToOwner(keyFile);
        Files.write(certFile.toPath(), certificate.getEncoded());
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

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, List<GeneralName> names)
        throws Exception {
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
            keyPair.getPublic());
        builder.addExtension(
            Extension.keyUsage,
            false,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        if (!names.isEmpty()) {
            builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(names.toArray(new GeneralName[names.size()])));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static List<GeneralName> collectSubjectAltNames() {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add("localhost");
        hosts.add("127.0.0.1");
        hosts.add("::1");

        ServerConfig config = Config.server();
        addHost(hosts, config.getPublicBaseUrl());
        addHost(hosts, config.getEffectiveApiRoot());
        addHost(hosts, config.getServerAddress());

        List<GeneralName> names = new ArrayList<>();
        for (String host : hosts) {
            GeneralName name = generalName(host);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static GeneralName generalName(String host) {
        if (host == null) return null;
        String trimmed = host.trim();
        if (trimmed.isEmpty()) return null;
        return new GeneralName(isIpLiteral(trimmed) ? GeneralName.iPAddress : GeneralName.dNSName, trimmed);
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

    private static String sha256Fingerprint(byte[] data) throws Exception {
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

    private static void restrictToOwner(File file) {
        try {
            Files.setPosixFilePermissions(
                file.toPath(),
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to restrict permissions on {}", file.getAbsolutePath(), e);
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
