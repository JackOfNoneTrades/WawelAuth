package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.EarsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents FoamFix's bundled Ears copy from loading alongside standalone Ears, and avoids loading it in an MCP-named
 * development environment that its transformer does not support.
 *
 * FoamFix excludes its own classes from coremod transformation, so this has to run from an early Minecraft lifecycle
 * hook rather than mix directly into FoamFix. FoamFix 1.0.4 recognizes older standalone Ears builds but not the newer
 * Plateau distribution, which would otherwise cause both copies to transform the player renderer.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFoamFix {

    @Unique
    private static final String TRANSFORMER_CLASS = "pl.asie.foamfix.bugfixmod.coremod.BugfixModClassTransformer";

    @Inject(method = "startGame", at = @At("HEAD"))
    private void wawelauth$deduplicateFoamFixEars(CallbackInfo ci) {
        boolean standaloneInstalled = EarsCompat.isStandaloneInstalled();
        boolean deobfuscatedEnvironment = Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
        if (!standaloneInstalled && !deobfuscatedEnvironment) return;

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

            // FoamFix 1.0.4 caches the transformer decision separately from the config value.
            setFieldIfPresent(transformerClass, transformer, "applyEarsPatch", Boolean.FALSE);
            // Older builds store the derived transformer gate on their settings object.
            Object settings = transformerClass.getField("settings")
                .get(transformer);
            if (settings != null) setFieldIfPresent(settings.getClass(), settings, "helloMmcg", false);

            if (standaloneInstalled) {
                WawelAuth.LOG.info("Disabled FoamFix's bundled Ears renderer because standalone Ears is present");
            } else {
                WawelAuth.LOG.info("Disabled FoamFix's bundled Ears renderer in the deobfuscated development client");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            WawelAuth.LOG.error("Failed to disable FoamFix's duplicate Ears renderer", e);
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
