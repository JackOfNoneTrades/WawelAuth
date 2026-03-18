package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;

import org.fentanylsolutions.wawelauth.client.render.IModelBipedModernExt;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.SkinLayers3DConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * Taken from <a href="https://github.com/Roadhog360/SimpleSkinBackport">SimpleSkinBackport</a>
 * [MIT License]
 */
@Mixin(value = RenderingRegistry.class, priority = 999)
public class MixinRenderingRegistry {

    @Inject(method = "loadEntityRenderers", at = @At(value = "TAIL"), remap = false)
    private void setupModernModels(Map<Class<? extends Entity>, Render> rendererMap, CallbackInfo ci) {
        if (SkinLayers3DConfig.modernSkinSupport) {
            RenderManager.instance.entityRenderMap.forEach((entityClass, renderer) -> {
                ModelBiped modelBiped = null;
                if (renderer instanceof RenderPlayer renderPlayer) {
                    modelBiped = renderPlayer.modelBipedMain;
                } else if (renderer instanceof RenderBiped renderBiped) {
                    modelBiped = renderBiped.modelBipedMain;
                }
                if (modelBiped instanceof IModelBipedModernExt bipedExtended) {
                    if (wawelAuth$rendererCopiesPlayerSkin(renderer)) {
                        bipedExtended.wawelauth$initModern();
                    }
                }
            });
        }
    }

    @Unique
    private static boolean wawelAuth$rendererCopiesPlayerSkin(Render renderer) {
        String name = renderer.getClass()
            .getName();
        return name.equals("vazkii.botania.client.render.entity.RenderDoppleganger")
            || (name.equals("twilightforest.client.renderer.entity.RenderTFGiant"));
    }

}
