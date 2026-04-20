package org.fentanylsolutions.wawelauth.wawelclient.oauth;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class LittleSkinOAuthClient extends ProviderOAuthClient {

    private static final String DEVICE_CODE_URL = "https://open.littleskin.cn/oauth/device_code";
    private static final String TOKEN_URL = "https://open.littleskin.cn/oauth/token";
    private static final String MINECRAFT_TOKEN_URL = "https://littleskin.cn/api/yggdrasil/authserver/oauth";
    private static final String CLIENT_ID = "__LITTLESKIN_CLIENT_ID__";
    private static final String SCOPES = "openid offline_access Yggdrasil.PlayerProfiles.Select Yggdrasil.MinecraftToken.Create";

    @Override
    public boolean supports(String providerName) {
        return BuiltinProviders.isLittleSkinProvider(providerName);
    }

    @Override
    protected LoginResult completeLogin(OAuthTokens tokens, ClientProvider provider, Consumer<String> status,
        UUID profileUuidHint, String profileNameHint, String currentAccessToken) throws IOException {
        UUID selectedUuid = profileUuidHint;
        String selectedName = trimToNull(profileNameHint);

        if (selectedUuid == null || selectedName == null) {
            JsonObject idTokenPayload = parseJwtPayload(tokens.getIdToken());
            if (idTokenPayload.has("selectedProfile") && idTokenPayload.get("selectedProfile")
                .isJsonObject()) {
                JsonObject selectedProfile = idTokenPayload.getAsJsonObject("selectedProfile");
                if (selectedUuid == null && selectedProfile.has("id")
                    && !selectedProfile.get("id")
                        .isJsonNull()) {
                    selectedUuid = UuidUtil.fromUnsigned(
                        selectedProfile.get("id")
                            .getAsString());
                }
                if (selectedName == null && selectedProfile.has("name")
                    && !selectedProfile.get("name")
                        .isJsonNull()) {
                    selectedName = trimToNull(
                        selectedProfile.get("name")
                            .getAsString());
                }
            }
        }

        if (selectedUuid == null) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_missing_profile"));
        }

        status.accept(tr("wawelauth.gui.login.status.oauth_minecraft_token"));
        JsonObject request = new JsonObject();
        request.addProperty("uuid", UuidUtil.toUnsigned(selectedUuid));
        JsonObject response = postJson(MINECRAFT_TOKEN_URL, request, "Bearer " + tokens.getAccessToken(), provider);

        String accessToken = requireString(response, "accessToken");
        String clientToken = response.has("clientToken") && !response.get("clientToken")
            .isJsonNull() ? trimToNull(
                response.get("clientToken")
                    .getAsString())
                : null;

        if (response.has("selectedProfile") && response.get("selectedProfile")
            .isJsonObject()) {
            JsonObject selectedProfile = response.getAsJsonObject("selectedProfile");
            selectedUuid = UuidUtil.fromUnsigned(requireString(selectedProfile, "id"));
            selectedName = requireString(selectedProfile, "name");
        } else if (response.has("availableProfiles") && response.get("availableProfiles")
            .isJsonArray()) {
                JsonArray profiles = response.getAsJsonArray("availableProfiles");
                if (profiles.size() > 0 && profiles.get(0)
                    .isJsonObject()) {
                    JsonObject first = profiles.get(0)
                        .getAsJsonObject();
                    if (selectedName == null) {
                        selectedName = requireString(first, "name");
                    }
                }
            }

        if (selectedName == null) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_missing_profile"));
        }

        return new LoginResult(selectedName, selectedUuid, accessToken, tokens.getRefreshToken(), clientToken);
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
