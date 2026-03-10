package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.PropertySigner;
import org.fentanylsolutions.wawelauth.wawelcore.data.ProfileProperty;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureData;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Builds spec-shaped profile responses with signed texture properties.
 * Shared by session, profile query, and hasJoined endpoints.
 */
public class ProfileService {

    private final PropertySigner signer;
    private final Gson gson;

    public ProfileService(PropertySigner signer) {
        this.signer = signer;
        this.gson = new GsonBuilder().create();
    }

    /**
     * Builds a full profile response map suitable for JSON serialization.
     * Includes the textures property and optionally signs it.
     *
     * @param profile the profile to serialize
     * @param signed  whether to sign the textures property
     * @return a spec-shaped map: {id, name, properties[]}
     */
    public Map<String, Object> buildFullProfile(WawelProfile profile, boolean signed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", UuidUtil.toUnsigned(profile.getUuid()));
        result.put("name", profile.getName());

        List<Map<String, Object>> properties = new ArrayList<>();

        // Build textures property
        ProfileProperty texturesProp = buildTexturesProperty(profile, signed);
        if (texturesProp != null) {
            Map<String, Object> propMap = new LinkedHashMap<>();
            propMap.put("name", texturesProp.getName());
            propMap.put("value", texturesProp.getValue());
            if (texturesProp.hasSignature()) {
                propMap.put("signature", texturesProp.getSignature());
            }
            properties.add(propMap);
        }

        // Build uploadableTextures property (authlib-injector extension)
        String uploadable = profile.toUploadableTexturesValue();
        if (uploadable != null) {
            Map<String, Object> propMap = new LinkedHashMap<>();
            propMap.put("name", "uploadableTextures");
            propMap.put("value", uploadable);
            properties.add(propMap);
        }

        result.put("properties", properties);
        return result;
    }

    /**
     * Builds a simple profile reference (id + name only, no properties).
     * Used in authenticate/refresh responses for availableProfiles.
     */
    public Map<String, Object> buildSimpleProfile(WawelProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", UuidUtil.toUnsigned(profile.getUuid()));
        result.put("name", profile.getName());
        return result;
    }

    /**
     * Re-signs the textures property of an existing profile JSON object using
     * the local WawelAuth key. Used only for WawelAuth-aware clients when the
     * source profile came from a fallback provider.
     */
    public JsonObject resignTexturesProperty(JsonObject profile) {
        if (profile == null || !profile.has("properties")
            || !profile.get("properties")
                .isJsonArray()) {
            return profile;
        }

        JsonArray properties = profile.getAsJsonArray("properties");
        for (JsonElement element : properties) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }

            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !property.has("value")) {
                continue;
            }

            String name;
            String value;
            try {
                name = property.get("name")
                    .getAsString();
                value = property.get("value")
                    .getAsString();
            } catch (Exception e) {
                continue;
            }

            if (!"textures".equals(name) || value == null || value.isEmpty()) {
                continue;
            }

            ProfileProperty signedProperty = new ProfileProperty("textures", value);
            signer.signProperty(signedProperty);
            property.addProperty("signature", signedProperty.getSignature());
        }

        return profile;
    }

    private ProfileProperty buildTexturesProperty(WawelProfile profile, boolean signed) {
        String apiRoot = Config.server()
            .getApiRoot();

        TextureData textureData = new TextureData(System.currentTimeMillis(), profile.getUuid(), profile.getName());

        // Add SKIN if present
        String skinHash = profile.getSkinHash();
        if (skinHash != null && !skinHash.isEmpty()) {
            String url = buildTextureUrl(apiRoot, skinHash);
            textureData.putTexture(TextureType.SKIN, new TextureData.TextureRef(url, profile.getSkinModel()));
        }

        // Add CAPE if present
        String capeHash = profile.getCapeHash();
        if (capeHash != null && !capeHash.isEmpty()) {
            String url = buildTextureUrl(apiRoot, capeHash);
            TextureData.TextureRef capeRef = new TextureData.TextureRef(url);
            if (profile.isCapeAnimated()) {
                Map<String, String> meta = new HashMap<>();
                meta.put("animated", "true");
                capeRef.setMetadata(meta);
            }
            textureData.putTexture(TextureType.CAPE, capeRef);
        }

        // Add ELYTRA if present and allowed
        String elytraHash = profile.getElytraHash();
        if (elytraHash != null && !elytraHash.isEmpty()
            && Config.server()
                .getTextures()
                .isAllowElytra()) {
            String url = buildTextureUrl(apiRoot, elytraHash);
            textureData.putTexture(TextureType.ELYTRA, new TextureData.TextureRef(url));
        }

        // Serialize TextureData to JSON manually to control UUID format
        String textureJson = serializeTextureData(textureData);
        String base64Value = Base64.getEncoder()
            .encodeToString(textureJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ProfileProperty prop = new ProfileProperty("textures", base64Value);
        if (signed) {
            signer.signProperty(prop);
        }
        return prop;
    }

    /**
     * Serializes TextureData to JSON with unsigned UUID format (no dashes).
     * Cannot use GSON directly because profileId must be unsigned.
     */
    private String serializeTextureData(TextureData data) {
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", data.getTimestamp());
        root.addProperty("profileId", UuidUtil.toUnsigned(data.getProfileId()));
        root.addProperty("profileName", data.getProfileName());

        JsonObject textures = new JsonObject();
        for (Map.Entry<TextureType, TextureData.TextureRef> entry : data.getTextures()
            .entrySet()) {
            JsonObject texObj = new JsonObject();
            texObj.addProperty(
                "url",
                entry.getValue()
                    .getUrl());
            if (entry.getValue()
                .getMetadata() != null
                && !entry.getValue()
                    .getMetadata()
                    .isEmpty()) {
                JsonObject meta = new JsonObject();
                for (Map.Entry<String, String> m : entry.getValue()
                    .getMetadata()
                    .entrySet()) {
                    meta.addProperty(m.getKey(), m.getValue());
                }
                texObj.add("metadata", meta);
            }
            textures.add(
                entry.getKey()
                    .name(),
                texObj);
        }
        root.add("textures", textures);

        return gson.toJson(root);
    }

    private static String buildTextureUrl(String apiRoot, String hash) {
        if (apiRoot == null || apiRoot.isEmpty()) {
            return "/textures/" + hash;
        }
        String base = apiRoot.endsWith("/") ? apiRoot.substring(0, apiRoot.length() - 1) : apiRoot;
        return base + "/textures/" + hash;
    }
}
