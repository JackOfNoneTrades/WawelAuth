package org.fentanylsolutions.wawelauth.mixins.late.etfuturum;

import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import ganymedes01.etfuturum.client.renderer.tileentity.TileEntityFancySkullRenderer;

@Mixin(value = TileEntityFancySkullRenderer.class, remap = false)
public class MixinTileEntityFancySkullRenderer {

    @Inject(method = "renderSkull", at = @At("HEAD"), cancellable = true)
    private void wawelauth$renderPlayerSkullWithVanillaRenderer(float x, float y, float z, int meta, float rotation,
        int type, GameProfile profile, CallbackInfo ci) {
        if (type != 3 || TileEntitySkullRenderer.field_147536_b == null) {
            return;
        }

        TileEntitySkullRenderer.field_147536_b.func_152674_a(x, y, z, meta, rotation, type, profile);
        ci.cancel();
    }
}
