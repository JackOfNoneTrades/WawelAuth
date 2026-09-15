package org.fentanylsolutions.wawelauth.core;

import java.util.List;
import java.util.Set;

import org.fentanylsolutions.fentlib.core.FentEarlyMixinLoader;
import org.fentanylsolutions.wawelauth.WawelAuth;

import com.falsepattern.deploader.DeploaderStub;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@SuppressWarnings("unused")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixinLoader extends FentEarlyMixinLoader {

    static {
        DeploaderStub.bootstrap(false);
        DeploaderStub.runDepLoader();
        Deps.load();
    }

    // Keep programmatic loader references behind a class loaded only after the stub bootstrap.
    private static class Deps {

        static void load() {
            SqliteDependencies.load();
        }
    }

    @Override
    public String getMixinConfig() {
        return "mixins." + WawelAuth.MODID + ".early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return Mixins.getEarlyMixinsForLoader();
    }
}
