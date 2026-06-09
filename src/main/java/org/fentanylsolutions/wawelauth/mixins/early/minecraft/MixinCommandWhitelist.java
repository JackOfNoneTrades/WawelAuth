package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.command.server.CommandWhitelist;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.server.management.UserListWhitelist;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.GameProfile;

/**
 * Supports provider-qualified whitelist adds:
 * /whitelist add <username>@<fallbackName>
 */
@Mixin(CommandWhitelist.class)
public class MixinCommandWhitelist {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/PlayerProfileCache;func_152655_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // PlayerProfileCache.getGameProfileForUsername
    private GameProfile wawelauth$resolveQualifiedWhitelistEntry(PlayerProfileCache profileCache, String rawInput) {
        if (FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            return FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
        }

        // Reject unqualified names.
        return null;
    }

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/UserListWhitelist;func_152706_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // UserListWhitelist.getByName
    private GameProfile wawelauth$resolveQualifiedWhitelistRemove(UserListWhitelist whitelist, String rawInput) {
        if (FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            GameProfile resolved = FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
            if (resolved != null) {
                if (resolved.getName() != null) {
                    GameProfile byName = whitelist.func_152706_a(resolved.getName()); // UserListWhitelist.getByName
                    if (byName != null) {
                        return byName;
                    }
                }
                return resolved;
            }
        }

        // Reject unqualified names.
        return null;
    }
}
