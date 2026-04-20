package org.fentanylsolutions.wawelauth.wawelclient.oauth;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.google.gson.JsonObject;

public class ElyByOAuthClient extends ProviderOAuthClient {

    private static final String DEVICE_CODE_URL = "https://account.ely.by/api/oauth2/v1/devicecode";
    private static final String TOKEN_URL = "https://account.ely.by/api/oauth2/v1/token";
    private static final String ACCOUNT_INFO_URL = "https://account.ely.by/api/account/v1/info";
    private static final String CLIENT_ID = "wawel-auth";
    private static final String SCOPES = "account_info offline_access minecraft_server_session";

    @Override
    public boolean supports(String providerName) {
        return BuiltinProviders.isElyByProvider(providerName);
    }

    @Override
    public boolean supportsProfileValidation() {
        return true;
    }

    @Override
    public MinecraftProfile fetchProfile(String accessToken, ClientProvider provider) throws IOException {
        JsonObject profile = getJson(ACCOUNT_INFO_URL, "Bearer " + accessToken, provider);
        String username = requireString(profile, "username");
        String uuidText = requireString(profile, "uuid");
        return new MinecraftProfile(username, UUID.fromString(uuidText));
    }

    @Override
    protected LoginResult completeLogin(OAuthTokens tokens, ClientProvider provider, Consumer<String> status,
        UUID profileUuidHint, String profileNameHint, String currentAccessToken) throws IOException {
        status.accept(tr("wawelauth.gui.login.status.oauth_profile"));
        MinecraftProfile profile = fetchProfile(tokens.getAccessToken(), provider);
        return new LoginResult(
            profile.getName(),
            profile.getUuid(),
            tokens.getAccessToken(),
            tokens.getRefreshToken(),
            null);
    }

    @Override
    protected String getClientId() {
        return CLIENT_ID;
    }

    @Override
    protected String getClientSecret() {
        return null;
    }

    @Override
    protected String getTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    protected String getScopes() {
        return SCOPES;
    }

    @Override
    protected String getDeviceCodeUrl() {
        return DEVICE_CODE_URL;
    }
}
