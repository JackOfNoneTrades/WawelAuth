package org.fentanylsolutions.wawelauth.wawelnet;

import java.io.File;
import java.lang.reflect.Field;

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

    private static void setStatic(Class<?> owner, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
