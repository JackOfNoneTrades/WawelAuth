package org.fentanylsolutions.wawelauth.api;

import java.util.Collection;

import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

final class YggdrasilTexturePayload {

    private YggdrasilTexturePayload() {}

    static SkinModel extractSkinModel(GameProfile profile) {
        try {
            Collection<Property> textures = profile.getProperties()
                .get("textures");
            if (textures == null || textures.isEmpty()) return null;

            for (Property property : textures) {
                if (property == null) continue;
                String value = property.getValue();
                if (value == null || value.isEmpty()) continue;

                String json = new String(org.apache.commons.codec.binary.Base64.decodeBase64(value), "UTF-8");
                JsonObject root = new JsonParser().parse(json)
                    .getAsJsonObject();
                JsonObject tex = root.getAsJsonObject("textures");
                if (tex == null) continue;
                JsonObject skin = tex.getAsJsonObject("SKIN");
                if (skin == null) continue;
                JsonObject metadata = skin.getAsJsonObject("metadata");
                if (metadata == null) return SkinModel.CLASSIC;
                JsonElement model = metadata.get("model");
                if (model == null || !model.isJsonPrimitive()) return SkinModel.CLASSIC;
                return SkinModel.fromYggdrasil(model.getAsString());
            }
        } catch (Exception ignored) {}
        return null;
    }
}
