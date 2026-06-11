package org.fentanylsolutions.wawelauth.wawelnet;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLEngine;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HttpsContextProviderTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void createsServerSslEngineAndPersistsCertificate() throws Exception {
        File configDir = temp.newFolder("wawelauth");
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setPublicBaseUrl("https://127.0.0.1:25565");
        serverConfig.setServerAddress("127.0.0.1:25565");

        setStatic(Config.class, "dataConfigDir", configDir);
        setStatic(Config.class, "serverConfig", serverConfig);
        setStatic(HttpsContextProvider.class, "cachedContext", null);
        setStatic(HttpsContextProvider.class, "cachedFingerprint", null);

        SSLEngine engine = HttpsContextProvider.newServerEngine();

        Assert.assertFalse(engine.getUseClientMode());
        Assert.assertNotNull(HttpsContextProvider.getCertificateFingerprint());
        Assert.assertTrue(new File(configDir, "data/https-private.der").isFile());
        Assert.assertTrue(new File(configDir, "data/https-cert.der").isFile());
    }

    @Test
    public void regeneratesStaleCertificateAfterConfirmationAndBacksUpOldFiles() throws Exception {
        File configDir = temp.newFolder("wawelauth-stale");
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setPublicBaseUrl("https://old.example.test:25565");
        serverConfig.setServerAddress("old.example.test:25565");

        setStatic(Config.class, "dataConfigDir", configDir);
        setStatic(Config.class, "serverConfig", serverConfig);
        resetProviderCache();

        HttpsContextProvider.prepareForStartup();

        File dataDir = new File(configDir, "data");
        File privateKeyFile = new File(dataDir, "https-private.der");
        File certFile = new File(dataDir, "https-cert.der");
        byte[] oldCertBytes = Files.readAllBytes(certFile.toPath());

        serverConfig.setPublicBaseUrl("https://new.example.test:25565");
        serverConfig.setServerAddress("new.example.test:25565");
        resetProviderCache();

        String previousQueryResult = System.getProperty("fml.queryResult");
        System.setProperty("fml.queryResult", "confirm");
        try {
            HttpsContextProvider.prepareForStartup();
        } finally {
            if (previousQueryResult == null) {
                System.clearProperty("fml.queryResult");
            } else {
                System.setProperty("fml.queryResult", previousQueryResult);
            }
        }

        byte[] newCertBytes = Files.readAllBytes(certFile.toPath());
        Assert.assertFalse(Arrays.equals(oldCertBytes, newCertBytes));
        Assert.assertTrue(hasDnsSubjectAltName(loadCertificate(certFile), "new.example.test"));

        File backupRoot = new File(dataDir, "https-certificate-backups");
        File[] backups = backupRoot.listFiles(File::isDirectory);
        Assert.assertNotNull(backups);
        Assert.assertEquals(1, backups.length);
        Assert.assertTrue(new File(backups[0], privateKeyFile.getName()).isFile());
        Assert.assertArrayEquals(oldCertBytes, Files.readAllBytes(new File(backups[0], certFile.getName()).toPath()));
    }

    private static void resetProviderCache() throws Exception {
        setStatic(HttpsContextProvider.class, "cachedContext", null);
        setStatic(HttpsContextProvider.class, "cachedFingerprint", null);
    }

    private static X509Certificate loadCertificate(File file) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (FileInputStream in = new FileInputStream(file)) {
            return (X509Certificate) factory.generateCertificate(in);
        }
    }

    private static boolean hasDnsSubjectAltName(X509Certificate certificate, String host) throws Exception {
        Collection<List<?>> names = certificate.getSubjectAlternativeNames();
        if (names == null) {
            return false;
        }
        for (List<?> entry : names) {
            if (entry.size() >= 2 && Integer.valueOf(2)
                .equals(entry.get(0)) && host.equalsIgnoreCase(String.valueOf(entry.get(1)))) {
                return true;
            }
        }
        return false;
    }

    private static void setStatic(Class<?> owner, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
