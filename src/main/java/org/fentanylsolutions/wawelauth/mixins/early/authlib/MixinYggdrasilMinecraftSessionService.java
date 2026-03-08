package org.fentanylsolutions.wawelauth.mixins.early.authlib;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.gui.AnimatedCapeTexture;
import org.fentanylsolutions.wawelauth.client.gui.AnimatedCapeTracker;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;

/**
 * Mixin into authlib's YggdrasilMinecraftSessionService.
 * remap = false because authlib classes are NOT obfuscated by MCP.
 *
 * Injection points:
 * 1. Signature verification in getTextures(): try connection-trusted keys
 * 2. Domain whitelisting in getTextures(): check connection-trusted domains
 * 3. fillProfileProperties(): fetch from active provider's session server
 * 4. getTextures() return: detect animated capes from metadata
 */
@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public class MixinYggdrasilMinecraftSessionService {

    /**
     * Redirect Property.isSignatureValid(PublicKey) in getTextures().
     *
     * Try the Mojang key first (fast path for vanilla), then try the
     * profile-scoped trusted provider keys selected by SessionBridge.
     */
    @Redirect(
        method = "getTextures",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/properties/Property;isSignatureValid(Ljava/security/PublicKey;)Z"))
    private boolean wawelauth$verifyScopedKey(Property property, PublicKey mojangKey, GameProfile profile,
        boolean requireSecure) {
        // Try Mojang key first (fast path, covers vanilla case)
        if (property.isSignatureValid(mojangKey)) return true;

        // Try WawelAuth keys:
        // - active connection trusted keys during gameplay
        // - all configured provider keys in UI/off-session contexts
        WawelClient client = WawelClient.instance();
        if (client == null) return false;
        for (PublicKey key : client.getSessionBridge()
            .getTextureVerificationKeys(profile)) {
            if (property.isSignatureValid(key)) return true;
        }
        return false;
    }

    /**
     * Redirect static isWhitelistedDomain(String) in getTextures().
     *
     * isWhitelistedDomain is a private static method in authlib, so the
     * redirect handler takes only the static method's arg (String url).
     * No instance parameter.
     *
     * Check vanilla Mojang domains plus the profile-scoped trusted provider
     * skin domains selected by SessionBridge.
     */
    @Redirect(
        method = "getTextures",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService;isWhitelistedDomain(Ljava/lang/String;)Z"))
    private boolean wawelauth$checkScopedDomains(String url, GameProfile profile, boolean requireSecure) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            // Vanilla-equivalent fallback before WawelAuth client starts
            try {
                String host = new java.net.URI(url).getHost();
                return host != null && (host.endsWith(".minecraft.net") || host.endsWith(".mojang.com"));
            } catch (Exception e) {
                return false;
            }
        }
        return client.getSessionBridge()
            .isWhitelistedDomain(url, profile);
    }

    /**
     * Inject at HEAD of fillProfileProperties() to route profile property
     * fetching through the active provider's session server.
     *
     * Uses active provider context: ALL profiles on the current server
     * belong to the active provider, including other players' skins.
     * If no active provider is set, falls through to vanilla (Mojang).
     */
    @Inject(method = "fillProfileProperties", at = @At("HEAD"), cancellable = true)
    private void wawelauth$fillFromProvider(GameProfile profile, boolean requireSecure,
        CallbackInfoReturnable<GameProfile> cir) {
        if (profile.getId() == null) return;

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        GameProfile filled = client.getSessionBridge()
            .fillProfileFromProvider(profile, requireSecure);
        if (filled != null) {
            // Vanilla fillProfileProperties modifies the original profile and
            // returns it. Other code (e.g. the player entity) holds a reference
            // to the original object. Copy fetched properties into it so that
            // SkinModelHelper.resolveFromProfileProperty() and similar lookups
            // see the textures property on the original reference.
            if (filled != profile) {
                profile.getProperties()
                    .clear();
                profile.getProperties()
                    .putAll(filled.getProperties());
            }
            cir.setReturnValue(profile);
        }
        // null → no active provider, fall through to vanilla (Mojang)
    }

    /**
     * Inject at RETURN of getTextures() to detect animated cape metadata.
     * When a CAPE texture has metadata "animated" = "true", download the
     * GIF asynchronously and register it in {@link AnimatedCapeTracker}.
     *
     * The textures map returned by authlib doesn't include custom metadata
     * (authlib 1.7.10 doesn't parse it), so we re-parse the raw textures
     * property from the profile to find the animated flag.
     */
    @Inject(method = "getTextures", at = @At("RETURN"))
    private void wawelauth$detectAnimatedCape(GameProfile profile, boolean requireSecure,
        CallbackInfoReturnable<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>> cir) {
        if (profile == null || profile.getId() == null) return;

        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> result = cir.getReturnValue();
        if (result == null || !result.containsKey(MinecraftProfileTexture.Type.CAPE)) return;

        UUID uuid = profile.getId();

        // Don't re-download if already tracked
        if (AnimatedCapeTracker.has(uuid)) return;

        // Parse the raw textures property for animated metadata
        try {
            for (Property prop : profile.getProperties()
                .get("textures")) {
                String decoded = new String(
                    Base64.getDecoder()
                        .decode(prop.getValue()),
                    StandardCharsets.UTF_8);
                JsonObject root = new JsonParser().parse(decoded)
                    .getAsJsonObject();
                if (!root.has("textures")) continue;

                JsonObject textures = root.getAsJsonObject("textures");
                if (!textures.has("CAPE")) continue;

                JsonObject capeObj = textures.getAsJsonObject("CAPE");
                if (!capeObj.has("metadata")) continue;

                JsonElement metaElem = capeObj.get("metadata");
                if (!metaElem.isJsonObject()) continue;

                JsonObject meta = metaElem.getAsJsonObject();
                if (!meta.has("animated")) continue;

                String animatedVal = meta.get("animated")
                    .getAsString();
                if (!"true".equals(animatedVal)) continue;

                // Found animated cape: download GIF and register
                String capeUrl = capeObj.get("url")
                    .getAsString();
                WawelAuth.debug("Detected animated cape for " + uuid + " at " + capeUrl);

                final String locationPath = "capes/ingame/" + uuid.toString()
                    .replace("-", "");
                CompletableFuture.supplyAsync(() -> {
                    try {
                        byte[] gifBytes = wawelauth$downloadBytes(capeUrl);
                        // Decode on background thread (CPU only, no OpenGL)
                        return AnimatedCapeTexture.decodeGif(gifBytes);
                    } catch (Exception e) {
                        WawelAuth.debug("Failed to download animated cape for " + uuid + ": " + e.getMessage());
                        return null;
                    }
                })
                    .whenComplete((decoded_, err) -> {
                        if (decoded_ == null) return;
                        // Create OpenGL texture on main thread
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> {
                                AnimatedCapeTexture tex = AnimatedCapeTexture.createFromDecoded(decoded_, locationPath);
                                if (tex != null) {
                                    AnimatedCapeTracker.register(uuid, tex);
                                }
                            });
                    });

                break; // only process first textures property
            }
        } catch (Exception e) {
            WawelAuth.debug("Failed to parse animated cape metadata: " + e.getMessage());
        }
    }

    private static byte[] wawelauth$downloadBytes(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "WawelAuth");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                return baos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }
}
