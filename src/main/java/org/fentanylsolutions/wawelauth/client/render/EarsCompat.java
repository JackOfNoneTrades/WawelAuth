package org.fentanylsolutions.wawelauth.client.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Compatibility bridge for standalone Ears and the copy bundled with FoamFix.
 *
 * Ears owns its replacement player model and skin layers when active. WawelAuth still owns skin resolution and passes
 * the resulting texture through Ears' normal processing hooks.
 */
@SideOnly(Side.CLIENT)
public final class EarsCompat {

    private static final String STANDALONE_EARS_CLASS = "com.unascribed.ears.Ears";
    private static final String PLATEAU_EARS_CLASS = "diy.y2k.five.plateau.Plateau";
    private static final String FOAMFIX_EARS_CLASS = "pl.asie.foamfix.repack.com.unascribed.ears.Ears";
    private static final String FOAMFIX_TRANSFORMER_CLASS = "pl.asie.foamfix.bugfixmod.coremod.BugfixModClassTransformer";

    private static volatile boolean providerResolved;
    private static Provider provider = Provider.NONE;
    private static MethodHandle skinProcessor;
    private static MethodHandle skinAssociator;
    private static Field slimLeftArm;
    private static Field slimRightArm;
    private static Field classicLeftArm;
    private static Field classicRightArm;
    private static Field skinUrls;
    private static Field slimUsers;
    private static boolean skinProcessorFailureLogged;
    private static boolean associationFailureLogged;
    private static boolean cacheFailureLogged;
    private static boolean armFailureLogged;

    private EarsCompat() {}

    public static boolean isRendererActive() {
        return getProvider() != Provider.NONE;
    }

    public static boolean isStandaloneInstalled() {
        return classExists(PLATEAU_EARS_CLASS) || classExists(STANDALONE_EARS_CLASS);
    }

    /** Resolve a WawelAuth-owned player's current skin before Ears falls back to its Mojang-only lookup. */
    public static boolean prepareSkin(UUID uuid, String name) {
        WawelClient client = WawelClient.instance();
        if (client == null || uuid == null) return false;

        ClientProvider provider = client.resolvePlayerProvider(uuid);
        if (provider == null) return false;

        ResourceLocation location = client.getTextureResolver()
            .getSkin(uuid, name, provider, false);
        SkinModel model = client.getSessionBridge()
            .resolveOfflineLocalSkinModel(uuid);
        if (model == null) {
            model = client.getTextureResolver()
                .getResolvedSkinModel(uuid, provider);
        }
        cacheSkin(
            uuid,
            (location != null ? location : WawelTextureResolver.getDefaultSkin()).toString(),
            model == SkinModel.SLIM);
        return true;
    }

    /** Process a locally loaded skin through Ears before it is registered as a texture. */
    public static BufferedImage processLocalSkin(BufferedImage image) {
        if (!isRendererActive()) return null;
        return processSkin(new ImageBufferDownload(), image);
    }

    /**
     * Seed Ears' legacy model lookup with metadata already resolved by WawelAuth. This prevents Ears from asking
     * Mojang about UUIDs belonging to a custom provider.
     */
    public static void cacheSkin(UUID uuid, String skinReference, boolean slim) {
        Provider active = getProvider();
        if (active == Provider.NONE || uuid == null || skinReference == null) return;

        try {
            updateLegacyModelCache(active, uuid, skinReference, slim);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!cacheFailureLogged) {
                cacheFailureLogged = true;
                WawelAuth.LOG.error("Failed to pass WawelAuth skin metadata to " + active.displayName, e);
            }
        }
    }

    /**
     * Allow Ears to preserve its metadata and associate it with WawelAuth's provider-aware texture object.
     *
     * @return the Ears-processed image, or {@code null} when no Ears renderer is active or processing failed
     */
    public static BufferedImage processSkin(ImageBufferDownload subject, BufferedImage image) {
        Provider active = getProvider();
        if (active == Provider.NONE) return null;

        try {
            MethodHandle processor = skinProcessor;
            if (processor == null) {
                processor = findSkinProcessor(active);
                skinProcessor = processor;
            }
            return (BufferedImage) processor.invokeExact(subject, image);
        } catch (Throwable e) {
            rethrowFatal(e);
            if (!skinProcessorFailureLogged) {
                skinProcessorFailureLogged = true;
                WawelAuth.LOG.error("Failed to pass a resolved skin through " + active.displayName, e);
            }
            return null;
        }
    }

    /** Associate an Ears-processed image with the texture object used by Minecraft's texture manager. */
    public static void associateSkin(Object texture, BufferedImage image) {
        Provider active = getProvider();
        if (active == Provider.NONE || !(texture instanceof ThreadDownloadImageData) || image == null) return;

        try {
            ThreadDownloadImageData associationTexture = (ThreadDownloadImageData) texture;
            MethodHandle associator = skinAssociator;
            if (associator == null) {
                associator = findSkinAssociator(active);
                skinAssociator = associator;
            }
            associator.invokeExact(associationTexture, image);
        } catch (Throwable e) {
            rethrowFatal(e);
            if (!associationFailureLogged) {
                associationFailureLogged = true;
                WawelAuth.LOG.error("Failed to associate a WawelAuth texture with " + active.displayName, e);
            }
        }
    }

    /** Replace WawelAuth's already-registered local skin texture with an Ears-aware static texture. */
    public static void replaceLocalSkinTexture(ResourceLocation location, BufferedImage image) {
        if (!isLocalSkinLocation(location) || !isRendererActive()) return;

        BufferedImage processed = processLocalSkin(image);
        if (processed == null) return;

        StaticSkinTexture texture = new StaticSkinTexture(processed);
        Minecraft.getMinecraft()
            .getTextureManager()
            .deleteTexture(location);
        Minecraft.getMinecraft()
            .getTextureManager()
            .loadTexture(location, texture);
        associateSkin(texture, processed);
    }

    /**
     * Ears normally asks Mojang's legacy lookup for slim-arm metadata. Re-apply WawelAuth's provider-aware result after
     * that hook so custom-provider skins use their declared model.
     */
    public static void applySkinModel(RenderPlayer renderer, AbstractClientPlayer player) {
        if (!SkinLayers3DConfig.modernSkinSupport) return;

        Provider active = getProvider();
        if (active == Provider.NONE) return;

        try {
            boolean slim = SkinModelHelper.getSkinModel(player) == SkinModel.SLIM;
            updateLegacyModelCache(
                active,
                player.getUniqueID(),
                player.getLocationSkin()
                    .toString(),
                slim);
            ensureArmFields(active);
            renderer.modelBipedMain.bipedLeftArm = getArm(slim ? slimLeftArm : classicLeftArm);
            renderer.modelBipedMain.bipedRightArm = getArm(slim ? slimRightArm : classicRightArm);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!armFailureLogged) {
                armFailureLogged = true;
                WawelAuth.LOG.error("Failed to apply WawelAuth skin-model metadata to " + active.displayName, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateLegacyModelCache(Provider active, UUID uuid, String skinReference, boolean slim)
        throws ReflectiveOperationException {
        ensureLegacyFields(active);
        Map<UUID, String> urls = (Map<UUID, String>) skinUrls.get(null);
        synchronized (urls) {
            urls.put(uuid, skinReference);

            Set<UUID> slimSet = (Set<UUID>) slimUsers.get(null);
            if (slim) {
                slimSet.add(uuid);
            } else {
                slimSet.remove(uuid);
            }
        }
    }

    private static ModelRenderer getArm(Field field) throws IllegalAccessException {
        ModelRenderer arm = (ModelRenderer) field.get(null);
        if (arm == null) throw new IllegalStateException("Ears player model is not initialized");
        return arm;
    }

    private static synchronized void ensureArmFields(Provider active) throws ReflectiveOperationException {
        if (slimLeftArm != null) return;

        Class<?> earsClass = Class.forName(active.className, true, Launch.classLoader);
        slimLeftArm = publicField(earsClass, "slimLeftArm");
        slimRightArm = publicField(earsClass, "slimRightArm");
        classicLeftArm = publicField(earsClass, "fatLeftArm");
        classicRightArm = publicField(earsClass, "fatRightArm");
    }

    private static synchronized void ensureLegacyFields(Provider active) throws ReflectiveOperationException {
        if (skinUrls != null) return;

        Class<?> legacyHelper = Class.forName(active.legacyHelperClassName, true, Launch.classLoader);
        skinUrls = privateField(legacyHelper, "skinUrls");
        slimUsers = privateField(legacyHelper, "slimUsers");
    }

    private static Field publicField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field privateField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static MethodHandle findSkinProcessor(Provider active) throws ReflectiveOperationException {
        Class<?> earsClass = Class.forName(active.className, true, Launch.classLoader);
        return MethodHandles.publicLookup()
            .findStatic(
                earsClass,
                active.skinProcessorName,
                MethodType.methodType(BufferedImage.class, ImageBufferDownload.class, BufferedImage.class));
    }

    private static MethodHandle findSkinAssociator(Provider active) throws ReflectiveOperationException {
        Class<?> earsClass = Class.forName(active.className, true, Launch.classLoader);
        return MethodHandles.publicLookup()
            .findStatic(
                earsClass,
                "checkSkin",
                MethodType.methodType(void.class, ThreadDownloadImageData.class, BufferedImage.class));
    }

    private static void rethrowFatal(Throwable throwable) {
        if (throwable instanceof ThreadDeath) throw (ThreadDeath) throwable;
        if (throwable instanceof VirtualMachineError) throw (VirtualMachineError) throwable;
    }

    private static boolean isLocalSkinLocation(ResourceLocation location) {
        if (location == null || !"wawelauth".equals(location.getResourceDomain())) return false;
        String path = location.getResourcePath();
        return path.startsWith("offline_skins/") || path.startsWith("upload_preview/skin/");
    }

    private static Provider getProvider() {
        if (!providerResolved) {
            synchronized (EarsCompat.class) {
                if (!providerResolved) {
                    provider = resolveProvider();
                    providerResolved = true;
                    if (provider != Provider.NONE) {
                        WawelAuth.LOG.info("Ears compatibility active for " + provider.displayName);
                    }
                }
            }
        }
        return provider;
    }

    private static Provider resolveProvider() {
        if (classExists(PLATEAU_EARS_CLASS)) {
            stabilizePlateauSkinLookup();
            return Provider.PLATEAU;
        }
        if (classExists(STANDALONE_EARS_CLASS)) return Provider.STANDALONE;
        if (isFoamFixEarsActive()) return Provider.FOAMFIX;
        return Provider.NONE;
    }

    /**
     * Plateau caches getLocationSkin from the concrete class of the first player it sees. WawelAuth's account preview
     * is a player subclass, so the cached Method cannot later be invoked on the in-world player. Pin it to their common
     * superclass before either one is rendered.
     */
    private static void stabilizePlateauSkinLookup() {
        try {
            Class<?> plateau = Class.forName(PLATEAU_EARS_CLASS, true, Launch.classLoader);
            Method method;
            try {
                method = AbstractClientPlayer.class.getMethod("getLocationSkin");
            } catch (NoSuchMethodException ignored) {
                method = AbstractClientPlayer.class.getMethod("func_110306_p");
            }
            Field field = privateField(plateau, "getLocationSkin");
            field.set(null, method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WawelAuth.LOG.error("Failed to stabilize standalone Ears (Plateau) skin lookup", e);
        }
    }

    private static boolean isFoamFixEarsActive() {
        if (!classExists(FOAMFIX_TRANSFORMER_CLASS)) return false;
        try {
            Class<?> transformerClass = Class.forName(FOAMFIX_TRANSFORMER_CLASS, false, Launch.classLoader);
            Object transformer = transformerClass.getField("instance")
                .get(null);
            return transformer != null && (Boolean) transformerClass.getMethod("applyEarsPatch")
                .invoke(transformer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WawelAuth.LOG.warn("Could not determine whether FoamFix's bundled Ears renderer is active", e);
            return false;
        }
    }

    private static boolean classExists(String name) {
        try {
            return Launch.classLoader.getClassBytes(name) != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private enum Provider {

        NONE(null, null, null, "Ears"),
        STANDALONE(STANDALONE_EARS_CLASS, "com.unascribed.ears.legacy.LegacyHelper", "interceptParseUserSkin",
            "standalone Ears"),
        PLATEAU(PLATEAU_EARS_CLASS, "com.unascribed.ears.legacy.LegacyHelper", "interceptProcessSkin",
            "standalone Ears (Plateau)"),
        FOAMFIX(FOAMFIX_EARS_CLASS, "pl.asie.foamfix.repack.com.unascribed.ears.legacy.LegacyHelper",
            "interceptParseUserSkin", "FoamFix's bundled Ears");

        private final String className;
        private final String legacyHelperClassName;
        private final String skinProcessorName;
        private final String displayName;

        Provider(String className, String legacyHelperClassName, String skinProcessorName, String displayName) {
            this.className = className;
            this.legacyHelperClassName = legacyHelperClassName;
            this.skinProcessorName = skinProcessorName;
            this.displayName = displayName;
        }
    }

    private static final class StaticSkinTexture extends ThreadDownloadImageData {

        private StaticSkinTexture(BufferedImage image) {
            super(null, null, null, null);
            setBufferedImage(image);
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
            // The decoded image is already installed; there is no remote download to start.
        }
    }
}
