package org.fentanylsolutions.wawelauth.mixins.early.authlib;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.animatedcape.AnimatedCapeTexture;
import org.fentanylsolutions.wawelauth.client.render.animatedcape.AnimatedCapeTracker;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderRoutedHttp;
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
 * remap = false because authlib is not obfuscated.
 *
 * Hooks: signature verification, domain whitelisting, profile fetching,
 * provider context skip, animated cape detection.
 */
@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public class MixinYggdrasilMinecraftSessionService {

    @Inject(method = "getTextures", at = @At("HEAD"))
    private void wawelauth$refreshProfileBeforeTextureVerify(GameProfile profile, boolean requireSecure,
        CallbackInfoReturnable<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>> cir) {
        if (profile == null || profile.getId() == null) {
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }

        // Provider context set = resolver already filled the profile
        ClientProvider providerContext = client.getSessionBridge()
            .getActiveProviderContext();
        if (providerContext != null) {
            return;
        }

        // In-world: resolver handles skin fetching per-player. Don't fill here,
        // would use wrong provider for other players.
        if (net.minecraft.client.Minecraft.getMinecraft().theWorld != null) {
            return;
        }
    }

    /** Redirect signature check to use provider context key, falling back to Mojang. */
    @Redirect(
        method = "getTextures",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/properties/Property;isSignatureValid(Ljava/security/PublicKey;)Z"))
    private boolean wawelauth$verifyScopedKey(Property property, PublicKey mojangKey, GameProfile profile,
        boolean requireSecure) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return property.isSignatureValid(mojangKey);
        }

        // Check vanilla Mojang key first if allowed
        if (client.getSessionBridge()
            .isVanillaTextureTrustAllowed() && property.isSignatureValid(mojangKey)) {
            return true;
        }

        // Check provider-scoped keys
        for (PublicKey key : client.getSessionBridge()
            .getTextureVerificationKeys()) {
            if (property.isSignatureValid(key)) return true;
        }
        return false;
    }

    /** Redirect domain whitelist to use provider context domains, falling back to vanilla. */
    @Redirect(
        method = "getTextures",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService;isWhitelistedDomain(Ljava/lang/String;)Z"))
    private boolean wawelauth$checkScopedDomains(String url, GameProfile profile, boolean requireSecure) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            try {
                String host = new java.net.URI(url).getHost();
                return host != null && (host.endsWith(".minecraft.net") || host.endsWith(".mojang.com"));
            } catch (Exception e) {
                return false;
            }
        }
        return client.getSessionBridge()
            .isWhitelistedDomain(url);
    }

    /** Route fillProfileProperties through the active provider's session server. */
    @Inject(method = "fillProfileProperties", at = @At("HEAD"), cancellable = true)
    private void wawelauth$fillFromProvider(GameProfile profile, boolean requireSecure,
        CallbackInfoReturnable<GameProfile> cir) {
        if (profile.getId() == null) return;

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        // ThreadLocal provider context (set by WawelTextureResolver.doFetch)
        ClientProvider providerContext = client.getSessionBridge()
            .getActiveProviderContext();
        if (providerContext != null) {
            GameProfile filled = client.getSessionBridge()
                .fillProfileFromProvider(providerContext, profile, requireSecure);
            if (filled != null) {
                copyFetchedProperties(profile, filled);
                cir.setReturnValue(profile);
            }
            return;
        }

        // In-world: resolver handles profile fetching with correct provider
        if (net.minecraft.client.Minecraft.getMinecraft().theWorld != null) {
            return;
        }
        // Not in-world: fall through to vanilla Mojang
    }

    private static void copyFetchedProperties(GameProfile profile, GameProfile filled) {
        if (profile == null || filled == null || filled == profile) {
            return;
        }
        profile.getProperties()
            .clear();
        profile.getProperties()
            .putAll(filled.getProperties());
    }

    /** Detect animated cape metadata from getTextures() result. */
    @Inject(method = "getTextures", at = @At("RETURN"))
    private void wawelauth$detectAnimatedCape(GameProfile profile, boolean requireSecure,
        CallbackInfoReturnable<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>> cir) {
        if (profile == null || profile.getId() == null) return;

        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> result = cir.getReturnValue();
        if (result == null || !result.containsKey(MinecraftProfileTexture.Type.CAPE)) return;

        UUID uuid = profile.getId();

        if (AnimatedCapeTracker.has(uuid)) return;

        WawelClient client = WawelClient.instance();
        if (client == null) return;

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

                String capeUrl = capeObj.get("url")
                    .getAsString();
                WawelAuth.debug("Detected animated cape for " + uuid + " at " + capeUrl);

                // Use ThreadLocal provider context, or resolve from connection
                ClientProvider provider = client.getSessionBridge()
                    .getActiveProviderContext();
                if (provider == null) {
                    provider = client.resolvePlayerProvider(uuid);
                }
                final ClientProvider downloadProvider = provider;

                final String locationPath = "capes/ingame/" + uuid.toString()
                    .replace("-", "");
                CompletableFuture.supplyAsync(() -> {
                    try {
                        byte[] gifBytes = wawelauth$downloadBytes(capeUrl, downloadProvider);
                        return AnimatedCapeTexture.decodeGif(gifBytes);
                    } catch (Exception e) {
                        WawelAuth.debug("Failed to download animated cape for " + uuid + ": " + e.getMessage());
                        return null;
                    }
                })
                    .whenComplete((decoded_, err) -> {
                        if (decoded_ == null) return;
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> {
                                AnimatedCapeTexture tex = AnimatedCapeTexture.createFromDecoded(decoded_, locationPath);
                                if (tex != null) {
                                    AnimatedCapeTracker.register(uuid, tex);
                                }
                            });
                    });

                break;
            }
        } catch (Exception e) {
            WawelAuth.debug("Failed to parse animated cape metadata: " + e.getMessage());
        }
    }

    private static byte[] wawelauth$downloadBytes(String urlStr, ClientProvider provider) throws Exception {
        return ProviderRoutedHttp
            .downloadBytes(urlStr, provider, 10_000, 15_000, "WawelAuth", "Animated cape download");
    }
}
