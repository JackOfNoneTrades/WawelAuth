package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.JsonConfigIO;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

@SuppressWarnings("unused")
public class GuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    public static class ConfigGui extends GuiConfig {

        private static final String LOCAL_CONFIG_FILE = "local.json";
        private static final String CLIENT_CONFIG_FILE = "client.json";
        private static final String SKINLAYERS_CONFIG_FILE = "skinlayers.json";

        private static final String CAT_DEBUG = "debug";
        private static final String CAT_AUTH = "auth";
        private static final String CAT_SKINS = "skins";

        private static final String KEY_DEBUG_MODE = "debugMode";
        private static final String KEY_USE_OS_CONFIG_DIR = "useOsConfigDir";
        private static final String KEY_DEFAULT_PROVIDER = "defaultProvider";
        private static final String KEY_DISABLE_SKIN_UPLOAD = "disableSkinUpload";
        private static final String KEY_DISABLE_CAPE_UPLOAD = "disableCapeUpload";
        private static final String KEY_DISABLE_TEXTURE_RESET = "disableTextureReset";
        private static final String KEY_MODERN_SKIN_SUPPORT = "modernSkinSupport";
        private static final String KEY_3D_ENABLED = "skin3dEnabled";
        private static final String KEY_3D_ENABLE_HAT = "skin3dEnableHat";
        private static final String KEY_3D_ENABLE_JACKET = "skin3dEnableJacket";
        private static final String KEY_3D_ENABLE_LEFT_SLEEVE = "skin3dEnableLeftSleeve";
        private static final String KEY_3D_ENABLE_RIGHT_SLEEVE = "skin3dEnableRightSleeve";
        private static final String KEY_3D_ENABLE_LEFT_PANTS = "skin3dEnableLeftPants";
        private static final String KEY_3D_ENABLE_RIGHT_PANTS = "skin3dEnableRightPants";
        private static final String KEY_3D_BASE_VOXEL_SIZE = "skin3dBaseVoxelSize";
        private static final String KEY_3D_BODY_WIDTH_VOXEL_SIZE = "skin3dBodyVoxelWidthSize";
        private static final String KEY_3D_HEAD_VOXEL_SIZE = "skin3dHeadVoxelSize";
        private static final String KEY_3D_SKULL_VOXEL_SIZE = "skin3dSkullVoxelSize";
        private static final String KEY_3D_RENDER_DISTANCE_LOD = "skin3dRenderDistanceLOD";
        private static final String KEY_3D_ENABLE_SKULLS = "skin3dEnableSkulls";
        private static final String KEY_3D_FAST_RENDER = "skin3dFastRender";

        private final Configuration uiConfig;

        public ConfigGui(GuiScreen parentScreen) {
            this(parentScreen, buildUiModel());
        }

        private ConfigGui(GuiScreen parentScreen, UiModel model) {
            super(
                parentScreen,
                model.rootElements,
                WawelAuth.MODID,
                WawelAuth.MODID,
                false,
                false,
                I18n.format("wawelauth.configgui.title"));
            this.uiConfig = model.uiConfig;
        }

        @Override
        public void initGui() {
            super.initGui();
            WawelAuth.debug("Initializing client.json config gui");
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            WawelAuth.debug("Config button id " + button.id + " pressed");
            super.actionPerformed(button);
            if (button.id == 2000) {
                saveClientConfigJson();
            }
        }

        private void saveClientConfigJson() {
            WawelAuth.debug("Saving config json files");

            LocalConfig localConfig = Config.local() != null ? Config.local() : new LocalConfig();
            localConfig.setDebugMode(bool(CAT_DEBUG, KEY_DEBUG_MODE, localConfig.isDebugMode()));
            localConfig.setUseOsConfigDir(bool(CAT_AUTH, KEY_USE_OS_CONFIG_DIR, localConfig.isUseOsConfigDir()));
            JsonConfigIO.save(new File(localConfigDir(), LOCAL_CONFIG_FILE), localConfig);

            File targetConfigDir = Config.resolveDataConfigDir(localConfig.isUseOsConfigDir());
            if (!targetConfigDir.exists() && !targetConfigDir.mkdirs()) {
                WawelAuth.LOG.error("Failed to create config directory: {}", targetConfigDir);
            }

            ClientConfig clientConfig = JsonConfigIO
                .load(new File(targetConfigDir, CLIENT_CONFIG_FILE), ClientConfig.class);

            String defaultProvider = string(CAT_AUTH, KEY_DEFAULT_PROVIDER, safe(clientConfig.getDefaultProvider()))
                .trim();
            clientConfig.setDefaultProvider(defaultProvider.isEmpty() ? null : defaultProvider);

            clientConfig.setDisableSkinUpload(
                sanitize(stringList(CAT_SKINS, KEY_DISABLE_SKIN_UPLOAD, toArray(clientConfig.getDisableSkinUpload()))));
            clientConfig.setDisableCapeUpload(
                sanitize(stringList(CAT_SKINS, KEY_DISABLE_CAPE_UPLOAD, toArray(clientConfig.getDisableCapeUpload()))));
            clientConfig.setDisableTextureReset(
                sanitize(
                    stringList(CAT_SKINS, KEY_DISABLE_TEXTURE_RESET, toArray(clientConfig.getDisableTextureReset()))));

            JsonConfigIO.save(new File(targetConfigDir, CLIENT_CONFIG_FILE), clientConfig);
            saveSkinLayers3dConfig(localConfigDir());
            Config.reload();
            WawelAuth.proxy.onConfigReload();
        }

        private void saveSkinLayers3dConfig(File configDir) {
            SkinLayers3DConfig.SkinLayers3DConfigData data = new SkinLayers3DConfig.SkinLayers3DConfigData();
            data.modernSkinSupport = bool(CAT_SKINS, KEY_MODERN_SKIN_SUPPORT, data.modernSkinSupport);
            data.enabled = bool(CAT_SKINS, KEY_3D_ENABLED, data.enabled);
            data.enableHat = bool(CAT_SKINS, KEY_3D_ENABLE_HAT, data.enableHat);
            data.enableJacket = bool(CAT_SKINS, KEY_3D_ENABLE_JACKET, data.enableJacket);
            data.enableLeftSleeve = bool(CAT_SKINS, KEY_3D_ENABLE_LEFT_SLEEVE, data.enableLeftSleeve);
            data.enableRightSleeve = bool(CAT_SKINS, KEY_3D_ENABLE_RIGHT_SLEEVE, data.enableRightSleeve);
            data.enableLeftPants = bool(CAT_SKINS, KEY_3D_ENABLE_LEFT_PANTS, data.enableLeftPants);
            data.enableRightPants = bool(CAT_SKINS, KEY_3D_ENABLE_RIGHT_PANTS, data.enableRightPants);
            data.baseVoxelSize = floatValue(CAT_SKINS, KEY_3D_BASE_VOXEL_SIZE, data.baseVoxelSize);
            data.bodyVoxelWidthSize = floatValue(CAT_SKINS, KEY_3D_BODY_WIDTH_VOXEL_SIZE, data.bodyVoxelWidthSize);
            data.headVoxelSize = floatValue(CAT_SKINS, KEY_3D_HEAD_VOXEL_SIZE, data.headVoxelSize);
            data.skullVoxelSize = floatValue(CAT_SKINS, KEY_3D_SKULL_VOXEL_SIZE, data.skullVoxelSize);
            data.renderDistanceLOD = intValue(CAT_SKINS, KEY_3D_RENDER_DISTANCE_LOD, data.renderDistanceLOD);
            data.enableSkulls = bool(CAT_SKINS, KEY_3D_ENABLE_SKULLS, data.enableSkulls);
            data.fastRender = bool(CAT_SKINS, KEY_3D_FAST_RENDER, data.fastRender);

            JsonConfigIO.save(new File(configDir, SKINLAYERS_CONFIG_FILE), data);
        }

        private boolean bool(String category, String key, boolean defaultValue) {
            return uiConfig.get(category, key, defaultValue)
                .getBoolean(defaultValue);
        }

        private String string(String category, String key, String defaultValue) {
            return uiConfig.get(category, key, defaultValue)
                .getString();
        }

        private List<String> stringList(String category, String key, String[] defaultValue) {
            return Arrays.asList(
                uiConfig.get(category, key, defaultValue)
                    .getStringList());
        }

        private int intValue(String category, String key, int defaultValue) {
            return uiConfig.get(category, key, defaultValue)
                .getInt(defaultValue);
        }

        private float floatValue(String category, String key, float defaultValue) {
            return (float) uiConfig.get(category, key, (double) defaultValue)
                .getDouble(defaultValue);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String[] toArray(List<String> values) {
            if (values == null || values.isEmpty()) {
                return new String[0];
            }
            return values.toArray(new String[values.size()]);
        }

        private static List<String> sanitize(List<String> values) {
            List<String> cleaned = new ArrayList<>();
            if (values == null) {
                return cleaned;
            }
            for (String value : values) {
                if (value == null) continue;
                String trimmed = value.trim();
                if (trimmed.isEmpty()) continue;
                cleaned.add(trimmed);
            }
            return cleaned;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static UiModel buildUiModel() {
            LocalConfig currentLocal = Config.local() != null ? Config.local() : new LocalConfig();
            LocalConfig defaultsLocal = new LocalConfig();
            ClientConfig current = Config.client() != null ? Config.client() : new ClientConfig();
            ClientConfig defaults = new ClientConfig();
            File localConfigDir = localConfigDir();
            SkinLayers3DConfig.SkinLayers3DConfigData current3d = JsonConfigIO.load(
                new File(localConfigDir, SKINLAYERS_CONFIG_FILE),
                SkinLayers3DConfig.SkinLayers3DConfigData.class);
            SkinLayers3DConfig.SkinLayers3DConfigData defaults3d = new SkinLayers3DConfig.SkinLayers3DConfigData();

            File uiCacheFile = new File(localConfigDir(), "client_gui_cache.cfg");
            Configuration uiConfig = new Configuration(uiCacheFile);

            Property debugMode = uiConfig.get(
                CAT_DEBUG,
                KEY_DEBUG_MODE,
                defaultsLocal.isDebugMode(),
                "Enable verbose WawelAuth debug logging on the client.");
            debugMode.set(currentLocal.isDebugMode());

            Property useOsConfigDir = uiConfig.get(
                CAT_AUTH,
                KEY_USE_OS_CONFIG_DIR,
                defaultsLocal.isUseOsConfigDir(),
                "Store account data in an OS-specific shared config directory.");
            useOsConfigDir.set(currentLocal.isUseOsConfigDir());

            Property defaultProvider = uiConfig.get(
                CAT_AUTH,
                KEY_DEFAULT_PROVIDER,
                safe(defaults.getDefaultProvider()),
                "Default provider name to preselect. Empty = no forced default.");
            defaultProvider.set(safe(current.getDefaultProvider()));

            Property disableSkinUpload = uiConfig.get(
                CAT_SKINS,
                KEY_DISABLE_SKIN_UPLOAD,
                toArray(defaults.getDisableSkinUpload()),
                "Regex list of provider names/API roots where skin upload is disabled.");
            disableSkinUpload.set(toArray(current.getDisableSkinUpload()));

            Property disableCapeUpload = uiConfig.get(
                CAT_SKINS,
                KEY_DISABLE_CAPE_UPLOAD,
                toArray(defaults.getDisableCapeUpload()),
                "Regex list of provider names/API roots where cape upload is disabled.");
            disableCapeUpload.set(toArray(current.getDisableCapeUpload()));

            Property disableTextureReset = uiConfig.get(
                CAT_SKINS,
                KEY_DISABLE_TEXTURE_RESET,
                toArray(defaults.getDisableTextureReset()),
                "Regex list of provider names/API roots where skin/cape reset is disabled.");
            disableTextureReset.set(toArray(current.getDisableTextureReset()));

            Property skin3dEnabled = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLED,
                defaults3d.enabled,
                "Master toggle: completely disable all 3D skin rendering (players and skulls).");
            skin3dEnabled.set(current3d.enabled);

            Property modernSkinSupport = uiConfig.get(
                CAT_SKINS,
                KEY_MODERN_SKIN_SUPPORT,
                defaults3d.modernSkinSupport,
                "Enable modern client skin support (64x64, slim arms, HD pass-through). Disable for legacy-only behavior. Restart recommended.");
            modernSkinSupport.set(current3d.modernSkinSupport);
            modernSkinSupport.setRequiresMcRestart(true);

            Property enableHat = uiConfig
                .get(CAT_SKINS, KEY_3D_ENABLE_HAT, defaults3d.enableHat, "3D overlays: render hat layer as voxels.");
            enableHat.set(current3d.enableHat);

            Property enableJacket = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLE_JACKET,
                defaults3d.enableJacket,
                "3D overlays: render jacket layer as voxels.");
            enableJacket.set(current3d.enableJacket);

            Property enableLeftSleeve = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLE_LEFT_SLEEVE,
                defaults3d.enableLeftSleeve,
                "3D overlays: render left sleeve layer as voxels.");
            enableLeftSleeve.set(current3d.enableLeftSleeve);

            Property enableRightSleeve = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLE_RIGHT_SLEEVE,
                defaults3d.enableRightSleeve,
                "3D overlays: render right sleeve layer as voxels.");
            enableRightSleeve.set(current3d.enableRightSleeve);

            Property enableLeftPants = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLE_LEFT_PANTS,
                defaults3d.enableLeftPants,
                "3D overlays: render left pants layer as voxels.");
            enableLeftPants.set(current3d.enableLeftPants);

            Property enableRightPants = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLE_RIGHT_PANTS,
                defaults3d.enableRightPants,
                "3D overlays: render right pants layer as voxels.");
            enableRightPants.set(current3d.enableRightPants);

            Property baseVoxelSize = uiConfig.get(
                CAT_SKINS,
                KEY_3D_BASE_VOXEL_SIZE,
                (double) defaults3d.baseVoxelSize,
                "3D overlays: base voxel size for limbs and body.",
                0.5d,
                2.5d);
            baseVoxelSize.set((double) current3d.baseVoxelSize);

            Property bodyVoxelWidthSize = uiConfig.get(
                CAT_SKINS,
                KEY_3D_BODY_WIDTH_VOXEL_SIZE,
                (double) defaults3d.bodyVoxelWidthSize,
                "3D overlays: body voxel width scale.",
                0.5d,
                2.5d);
            bodyVoxelWidthSize.set((double) current3d.bodyVoxelWidthSize);

            Property headVoxelSize = uiConfig.get(
                CAT_SKINS,
                KEY_3D_HEAD_VOXEL_SIZE,
                (double) defaults3d.headVoxelSize,
                "3D overlays: head voxel size scale.",
                0.5d,
                2.5d);
            headVoxelSize.set((double) current3d.headVoxelSize);

            Property skullVoxelSize = uiConfig.get(
                CAT_SKINS,
                KEY_3D_SKULL_VOXEL_SIZE,
                (double) defaults3d.skullVoxelSize,
                "3D overlays: skull voxel size scale.",
                0.5d,
                2.5d);
            skullVoxelSize.set((double) current3d.skullVoxelSize);

            Property renderDistanceLod = uiConfig.get(
                CAT_SKINS,
                KEY_3D_RENDER_DISTANCE_LOD,
                defaults3d.renderDistanceLOD,
                "3D overlays: distance in blocks before falling back to flat layer rendering.",
                2,
                128);
            renderDistanceLod.set(current3d.renderDistanceLOD);

            Property enableSkulls = uiConfig.get(
                CAT_SKINS,
                KEY_3D_ENABLE_SKULLS,
                defaults3d.enableSkulls,
                "3D overlays: enable skull voxel rendering.");
            enableSkulls.set(current3d.enableSkulls);

            Property fastRender = uiConfig.get(
                CAT_SKINS,
                KEY_3D_FAST_RENDER,
                defaults3d.fastRender,
                "3D overlays: render front faces as vanilla boxes, voxels for sides only.");
            fastRender.set(current3d.fastRender);

            uiConfig.getCategory(CAT_DEBUG)
                .setComment("Client debug settings.");
            uiConfig.getCategory(CAT_AUTH)
                .setComment("Client account/auth behavior.");
            uiConfig.getCategory(CAT_SKINS)
                .setComment("Client-side upload/texture policy and 3D skin layer settings.");

            uiConfig.getCategory(CAT_DEBUG)
                .setPropertyOrder(Arrays.asList(KEY_DEBUG_MODE));
            uiConfig.getCategory(CAT_AUTH)
                .setPropertyOrder(Arrays.asList(KEY_USE_OS_CONFIG_DIR, KEY_DEFAULT_PROVIDER));
            uiConfig.getCategory(CAT_SKINS)
                .setPropertyOrder(
                    Arrays.asList(
                        KEY_DISABLE_SKIN_UPLOAD,
                        KEY_DISABLE_CAPE_UPLOAD,
                        KEY_DISABLE_TEXTURE_RESET,
                        KEY_MODERN_SKIN_SUPPORT,
                        KEY_3D_ENABLED,
                        KEY_3D_ENABLE_HAT,
                        KEY_3D_ENABLE_JACKET,
                        KEY_3D_ENABLE_LEFT_SLEEVE,
                        KEY_3D_ENABLE_RIGHT_SLEEVE,
                        KEY_3D_ENABLE_LEFT_PANTS,
                        KEY_3D_ENABLE_RIGHT_PANTS,
                        KEY_3D_BASE_VOXEL_SIZE,
                        KEY_3D_BODY_WIDTH_VOXEL_SIZE,
                        KEY_3D_HEAD_VOXEL_SIZE,
                        KEY_3D_SKULL_VOXEL_SIZE,
                        KEY_3D_RENDER_DISTANCE_LOD,
                        KEY_3D_ENABLE_SKULLS,
                        KEY_3D_FAST_RENDER));

            List<IConfigElement> rootElements = new ArrayList<>();
            rootElements.add(new ConfigElement(uiConfig.getCategory(CAT_DEBUG)));
            rootElements.add(new ConfigElement(uiConfig.getCategory(CAT_AUTH)));
            rootElements.add(new ConfigElement(uiConfig.getCategory(CAT_SKINS)));
            return new UiModel(uiConfig, rootElements);
        }

        private static File localConfigDir() {
            File dir = Config.getLocalConfigDir();
            if (dir != null) {
                return dir;
            }
            dir = Config.getConfigDir();
            if (dir != null) {
                return dir;
            }
            return new File("config/wawelauth");
        }

        private static class UiModel {

            private final Configuration uiConfig;
            private final List<IConfigElement> rootElements;

            private UiModel(Configuration uiConfig, List<IConfigElement> rootElements) {
                this.uiConfig = uiConfig;
                this.rootElements = rootElements;
            }
        }
    }
}
