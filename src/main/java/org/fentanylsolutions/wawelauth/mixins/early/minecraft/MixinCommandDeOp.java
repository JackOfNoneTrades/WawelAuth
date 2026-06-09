package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.command.server.CommandDeOp;
import net.minecraft.server.management.UserListOps;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.GameProfile;

/**
 * Supports provider-qualified deop removes:
 * /deop <username>@<fallbackName>
 */
@Mixin(CommandDeOp.class)
public class MixinCommandDeOp {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/UserListOps;func_152700_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // UserListOps.getByName
    private GameProfile wawelauth$resolveQualifiedDeOpEntry(UserListOps userListOps, String rawInput) {
        if (FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            GameProfile resolved = FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
            if (resolved != null) {
                if (resolved.getName() != null) {
                    GameProfile byName = userListOps.func_152700_a(resolved.getName()); // UserListOps.getByName
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
