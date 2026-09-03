package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables FoamFix's bundled 1.8 skin backport before mod initialization.
 *
 * FoamFix excludes its own classes from coremod transformation, so this has to run from an early Minecraft lifecycle
 * hook rather than mix directly into FoamFix. WawelAuth owns the same skin parsing, model, and render paths; allowing
 * both to run replaces WawelAuth's player model and renders a second first-person overlay.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFoamFix {

    @Unique
    private static final String TRANSFORMER_CLASS = "pl.asie.foamfix.bugfixmod.coremod.BugfixModClassTransformer";

    @Inject(method = "startGame", at = @At("HEAD"))
    private void wawelauth$disableFoamFixModernSkinSupport(CallbackInfo ci) {
        Class<?> transformerClass;
        try {
            transformerClass = Class.forName(TRANSFORMER_CLASS);
        } catch (ClassNotFoundException ignored) {
            return;
        }

        try {
            Object transformer = transformerClass.getField("instance")
                .get(null);
            if (transformer == null) return;

            Object settings = transformerClass.getField("settings")
                .get(transformer);
            if (settings == null) return;

            settings.getClass()
                .getField("mc18SkinSupport")
                .setBoolean(settings, false);

            // FoamFix 1.0.4 caches the transformer decision separately from the config value.
            setFieldIfPresent(transformerClass, transformer, "applyEarsPatch", Boolean.FALSE);
            // Older builds store the derived transformer gate on their settings object.
            setFieldIfPresent(settings.getClass(), settings, "helloMmcg", false);

            WawelAuth.LOG.info("Disabled FoamFix mc18SkinSupport because WawelAuth provides modern skin support");
        } catch (ReflectiveOperationException | RuntimeException e) {
            WawelAuth.LOG.error("Failed to disable FoamFix mc18SkinSupport", e);
        }
    }

    @Unique
    private static void setFieldIfPresent(Class<?> ownerClass, Object owner, String name, Object value)
        throws IllegalAccessException {
        try {
            Field field = ownerClass.getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (NoSuchFieldException ignored) {}
    }
}
