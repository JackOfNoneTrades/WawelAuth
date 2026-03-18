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
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayersConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.JsonConfigIO;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

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

        private static final String CAT_LOCAL = "local";

        private static final String KEY_DEBUG_MODE = "debugMode";
        private static final String KEY_USE_OS_CONFIG_DIR = "useOsConfigDir";

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
            WawelAuth.debug("Initializing config gui");
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            WawelAuth.debug("Config button id " + button.id + " pressed");
            super.actionPerformed(button);
            if (button.id == 2000) {
                saveLocalConfig();
            }
        }

        private void saveLocalConfig() {
            WawelAuth.debug("Saving local config json");

            LocalConfig localConfig = Config.local() != null ? Config.local() : new LocalConfig();
            localConfig.setDebugMode(bool(CAT_LOCAL, KEY_DEBUG_MODE, localConfig.isDebugMode()));
            localConfig.setUseOsConfigDir(bool(CAT_LOCAL, KEY_USE_OS_CONFIG_DIR, localConfig.isUseOsConfigDir()));
            JsonConfigIO.save(new File(localConfigDir(), LOCAL_CONFIG_FILE), localConfig);

            // Invalidate ClientConfig pattern caches after GTNHLib saves its configs
            ClientConfig.invalidatePatternCache();

            Config.reload();
            WawelAuth.proxy.onConfigReload();
        }

        private boolean bool(String category, String key, boolean defaultValue) {
            return uiConfig.get(category, key, defaultValue)
                .getBoolean(defaultValue);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static UiModel buildUiModel() {
            LocalConfig currentLocal = Config.local() != null ? Config.local() : new LocalConfig();
            LocalConfig defaultsLocal = new LocalConfig();

            File uiCacheFile = new File(localConfigDir(), "client_gui_cache.cfg");
            Configuration uiConfig = new Configuration(uiCacheFile);

            Property debugMode = uiConfig.get(
                CAT_LOCAL,
                KEY_DEBUG_MODE,
                defaultsLocal.isDebugMode(),
                "Enable verbose WawelAuth debug logging on the client.");
            debugMode.set(currentLocal.isDebugMode());

            Property useOsConfigDir = uiConfig.get(
                CAT_LOCAL,
                KEY_USE_OS_CONFIG_DIR,
                defaultsLocal.isUseOsConfigDir(),
                "Store account data in an OS-specific shared config directory.");
            useOsConfigDir.set(currentLocal.isUseOsConfigDir());
            useOsConfigDir.setRequiresMcRestart(true);

            uiConfig.getCategory(CAT_LOCAL)
                .setComment("Local bootstrap settings (stored in local.json).");
            uiConfig.getCategory(CAT_LOCAL)
                .setPropertyOrder(Arrays.asList(KEY_DEBUG_MODE, KEY_USE_OS_CONFIG_DIR));

            List<IConfigElement> rootElements = new ArrayList<>();
            rootElements.add(new ConfigElement(uiConfig.getCategory(CAT_LOCAL)));

            // Add GTNHLib-managed config elements for ClientConfig and SkinLayers3DConfig
            try {
                rootElements.addAll(ConfigurationManager.getConfigElements(ClientConfig.class, true));
                rootElements.addAll(ConfigurationManager.getConfigElements(SkinLayers3DConfig.class, true));
                rootElements.addAll(ConfigurationManager.getConfigElements(SkinLayersConfig.class, true));
            } catch (ConfigException e) {
                WawelAuth.LOG.error("Failed to build GTNHLib config GUI elements", e);
            }

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
