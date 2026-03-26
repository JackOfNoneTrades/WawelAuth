package org.fentanylsolutions.wawelauth.client.render;

import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.resources.SkinManager;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.properties.Property;

/**
 * Resolves slim/classic arm model for rendered players.
 */
public final class SkinModelHelper {

    private static final WeakHashMap<AbstractClientPlayer, String> LAST_RESOLUTION = new WeakHashMap<>();

    private SkinModelHelper() {}

    public static SkinModel getSkinModel(AbstractClientPlayer player) {
        if (!SkinLayers3DConfig.modernSkinSupport) {
            debugResolution(player, SkinModel.CLASSIC, "modern_disabled");
            return SkinModel.CLASSIC;
        }

        SkinModel forced = resolveForced(player);
        if (forced != null) {
            debugResolution(player, forced, "forced");
            return forced;
        }

        SkinModel offlineLocal = resolveOfflineLocal(player);
        if (offlineLocal != null) {
            debugResolution(player, offlineLocal, "offline_local");
            return offlineLocal;
        }

        SkinModel fromResolver = resolveFromTextureResolver(player);
        if (fromResolver != null) {
            debugResolution(player, fromResolver, "texture_resolver");
            return fromResolver;
        }

        SkinModel fromSkinManager = resolveFromSkinManager(player);
        if (fromSkinManager != null) {
            debugResolution(player, fromSkinManager, "skin_manager");
            return fromSkinManager;
        }

        SkinModel fromProfile = resolveFromProfileProperty(player);
        if (fromProfile != null) {
            debugResolution(player, fromProfile, "profile_property");
            return fromProfile;
        }

        debugResolution(player, SkinModel.CLASSIC, "default");
        return SkinModel.CLASSIC;
    }

    private static SkinModel resolveForced(AbstractClientPlayer player) {
        if (player instanceof ISkinModelOverride) {
            return ((ISkinModelOverride) player).wawelauth$getForcedSkinModel();
        }
        return null;
    }

    private static SkinModel resolveOfflineLocal(AbstractClientPlayer player) {
        WawelClient client = WawelClient.instance();
        if (client == null || player == null) {
            return null;
        }
        return client.getSessionBridge()
            .resolveOfflineLocalSkinModel(player.getUniqueID());
    }

    private static SkinModel resolveFromTextureResolver(AbstractClientPlayer player) {
        WawelClient client = WawelClient.instance();
        if (client == null || player == null) {
            return null;
        }
        ClientProvider provider = client.resolvePlayerProvider(player.getUniqueID());
        if (provider == null) return null;
        return client.getTextureResolver()
            .getResolvedSkinModel(player.getUniqueID(), provider);
    }

    private static SkinModel resolveFromSkinManager(AbstractClientPlayer player) {
        try {
            SkinManager skinManager = Minecraft.getMinecraft()
                .func_152342_ad();
            if (skinManager == null) return null;

            @SuppressWarnings("unchecked")
            Map<Type, MinecraftProfileTexture> textures = skinManager.func_152788_a(player.getGameProfile());
            if (textures == null) return null;

            MinecraftProfileTexture skin = textures.get(Type.SKIN);
            if (skin == null) return null;

            // authlib 1.7.10 doesn't populate MinecraftProfileTexture.metadata
            // from the textures JSON, so getMetadata() returns null even for slim
            // skins. Return null to fall through to resolveFromProfileProperty()
            // which re-parses the raw base64 property and finds the metadata.
            String model = skin.getMetadata("model");
            if (model == null) return null;
            return SkinModel.fromYggdrasil(model);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SkinModel resolveFromProfileProperty(AbstractClientPlayer player) {
        try {
            Collection<Property> textures = player.getGameProfile()
                .getProperties()
                .get("textures");
            if (textures == null || textures.isEmpty()) return null;

            for (Property property : textures) {
                if (property == null) continue;
                String value = property.getValue();
                if (value == null || value.isEmpty()) continue;
                return parseModelFromTexturesValue(value);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static SkinModel parseModelFromTexturesValue(String valueBase64) {
        try {
            String json = new String(org.apache.commons.codec.binary.Base64.decodeBase64(valueBase64), "UTF-8");
            JsonObject root = new JsonParser().parse(json)
                .getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) return SkinModel.CLASSIC;

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) return SkinModel.CLASSIC;

            JsonObject metadata = skin.getAsJsonObject("metadata");
            if (metadata == null) return SkinModel.CLASSIC;

            JsonElement model = metadata.get("model");
            if (model == null || !model.isJsonPrimitive()) return SkinModel.CLASSIC;

            return SkinModel.fromYggdrasil(model.getAsString());
        } catch (Exception ignored) {
            return SkinModel.CLASSIC;
        }
    }

    private static void debugResolution(AbstractClientPlayer player, SkinModel model, String source) {
        String state = source + ":" + model.name();
        String previous = LAST_RESOLUTION.get(player);
        if (state.equals(previous)) return;
        LAST_RESOLUTION.put(player, state);
        WawelAuth
            .debug("Skin model resolved for " + player.getCommandSenderName() + ": " + model.name() + " via " + source);
    }
}
