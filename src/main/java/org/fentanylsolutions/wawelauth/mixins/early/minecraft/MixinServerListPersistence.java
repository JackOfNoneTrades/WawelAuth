package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.io.File;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.IServerListPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerListStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerList.class)
public abstract class MixinServerListPersistence implements IServerListPersistence {

    @Shadow
    @Final
    private Minecraft mc;
    @Shadow
    @Final
    private List<ServerData> servers;
    @Unique
    private ServerListStorage wawelauth$storage;

    @Inject(method = "loadServerList", at = @At("HEAD"), cancellable = true)
    private void wawelauth$load(CallbackInfo ci) {
        ci.cancel();
        if (wawelauth$storage == null) {
            wawelauth$storage = new ServerListStorage(new File(mc.mcDataDir, "servers.dat").toPath());
        }
        try {
            List<ServerData> loaded = wawelauth$storage.load();
            servers.clear();
            servers.addAll(loaded);
        } catch (Exception e) {
            WawelAuth.LOG.error("Could not load servers.dat; retaining the current list and blocking saves", e);
        }
    }

    @Inject(method = "saveServerList", at = @At("HEAD"), cancellable = true)
    private void wawelauth$save(CallbackInfo ci) {
        ci.cancel();
        wawelauth$saveSafely();
    }

    @Override
    public boolean wawelauth$isLoaded() {
        return wawelauth$storage != null && wawelauth$storage.isLoaded();
    }

    @Override
    public boolean wawelauth$isCurrent() {
        return wawelauth$storage != null && wawelauth$storage.isCurrent();
    }

    @Override
    public boolean wawelauth$saveSafely() {
        if (!mc.func_152345_ab()) {
            mc.func_152344_a(() -> ((ServerList) (Object) this).saveServerList());
            return false;
        }
        try {
            if (wawelauth$storage == null) {
                throw new IllegalStateException("Server list has not been loaded");
            }
            wawelauth$storage.save(servers);
            return true;
        } catch (Exception e) {
            WawelAuth.LOG.error("Could not save servers.dat; the existing file was not replaced", e);
            return false;
        }
    }
}
