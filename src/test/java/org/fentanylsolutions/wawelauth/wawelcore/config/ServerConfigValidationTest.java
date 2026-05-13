package org.fentanylsolutions.wawelauth.wawelcore.config;

import org.junit.Test;

public class ServerConfigValidationTest {

    @Test
    public void defaultConfigIsValid() {
        new ServerConfig().validateOrThrow();
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsZeroTlsHandshakeTimeout() {
        ServerConfig config = new ServerConfig();
        config.getHttp()
            .setTlsHandshakeTimeoutSeconds(0);

        config.validateOrThrow();
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsExcessiveTlsHandshakeTimeout() {
        ServerConfig config = new ServerConfig();
        config.getHttp()
            .setTlsHandshakeTimeoutSeconds(ServerConfig.MAX_TLS_HANDSHAKE_TIMEOUT_SECONDS + 1);

        config.validateOrThrow();
    }
}
