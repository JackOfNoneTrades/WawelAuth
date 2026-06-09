package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import java.util.Locale;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;
import serverutils.lib.util.ServerUtils;
import serverutils.lib.util.StringUtils;

@Mixin(value = Universe.class, remap = false)
public abstract class MixinUniverse {

    @Shadow(remap = false)
    public abstract ForgePlayer getPlayer(UUID id);

    @Inject(
        method = "getPlayer(Ljava/lang/CharSequence;)Lserverutils/lib/data/ForgePlayer;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void wawelauth$getProviderQualifiedPlayer(CharSequence nameOrId, CallbackInfoReturnable<ForgePlayer> cir) {
        if (nameOrId == null) {
            return;
        }

        String rawInput = nameOrId.toString();
        String normalized = rawInput.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }

        UUID id = StringUtils.fromString(normalized);
        if (id != null) {
            cir.setReturnValue(getPlayer(id));
            return;
        }

        if (normalized.equals(ServerUtils.FAKE_PLAYER_PROFILE.getName()
            .toLowerCase(Locale.ROOT))) {
            cir.setReturnValue(getPlayer(ServerUtils.FAKE_PLAYER_PROFILE.getId()));
            return;
        }

        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            EntityPlayerMP online = ProviderQualifiedPlayerLookup.findOnlinePlayerByName(rawInput);
            cir.setReturnValue(online == null ? null : getPlayer(online.getUniqueID()));
            return;
        }

        GameProfile profile = FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
        if (profile == null || profile.getId() == null) {
            cir.setReturnValue(null);
            return;
        }

        cir.setReturnValue(getPlayer(profile.getId()));
    }
}
