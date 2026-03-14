package org.fentanylsolutions.wawelauth.mixins.early.authlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.mojang.authlib.yggdrasil.YggdrasilGameProfileRepository;

/**
 * 1.7.10 authlib uses the legacy name-lookup endpoint:
 * https://api.mojang.com/profiles/{agent}
 * <p>
 * Modern Mojang/Microsoft lookup uses:
 * https://api.minecraftservices.com/{agent}/profile/lookup/bulk/byname
 * <p>
 * We patch only the URL argument and keep all original retry/pagination logic.
 */
@Mixin(value = YggdrasilGameProfileRepository.class, remap = false)
public class MixinYggdrasilGameProfileRepository {

    private static final String LEGACY_PREFIX = "https://api.mojang.com/profiles/";
    private static final String MODERN_BASE = "https://api.minecraftservices.com/";
    private static final String MODERN_SUFFIX = "/profile/lookup/bulk/byname";

    @ModifyArg(
        method = "findProfilesByNames",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/HttpAuthenticationService;constantURL(Ljava/lang/String;)Ljava/net/URL;"),
        index = 0)
    private String wawelauth$rewriteProfileLookupUrl(String originalUrl) {
        if (originalUrl == null || !originalUrl.startsWith(LEGACY_PREFIX)) {
            return originalUrl;
        }

        String agent = originalUrl.substring(LEGACY_PREFIX.length());
        if (agent.isEmpty()) {
            return originalUrl;
        }
        return MODERN_BASE + agent + MODERN_SUFFIX;
    }
}
