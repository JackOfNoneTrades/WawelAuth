package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.command.server.CommandPardonPlayer;
import net.minecraft.server.management.UserListBans;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.GameProfile;

/**
 * Supports provider-qualified pardons:
 * /pardon <username>@<fallbackName>
 */
@Mixin(CommandPardonPlayer.class)
public class MixinCommandPardonPlayer {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/UserListBans;func_152703_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // UserListBans.getBannedProfile
    private GameProfile wawelauth$resolveQualifiedPardonEntry(UserListBans bans, String rawInput) {
        if (FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            GameProfile resolved = FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
            if (resolved != null && resolved.getId() != null && bans.func_152702_a(resolved)) {
                return resolved;
            }
        }

        // Reject unqualified names.
        return null;
    }
}
