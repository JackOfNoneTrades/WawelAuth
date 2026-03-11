package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.NBTTagCompound;

import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public class MixinServerData implements IServerDataExt {

    @Unique
    private long wawelAccountId = -1;

    @Unique
    private String wawelProviderName;

    @Unique
    private String wawelLocalAuthFingerprint;

    @Unique
    private String wawelLocalAuthApiRoot;

    @Unique
    private String wawelLocalAuthPublicKeyBase64;

    @Unique
    private String wawelOriginalServerIp;

    @Unique
    private ServerCapabilities wawelCapabilities = ServerCapabilities.empty();

    @Override
    public long getWawelAccountId() {
        return wawelAccountId;
    }

    @Override
    public void setWawelAccountId(long id) {
        this.wawelAccountId = id;
    }

    @Override
    public String getWawelProviderName() {
        return wawelProviderName;
    }

    @Override
    public void setWawelProviderName(String name) {
        this.wawelProviderName = name;
    }

    @Override
    public String getWawelLocalAuthFingerprint() {
        return wawelLocalAuthFingerprint;
    }

    @Override
    public void setWawelLocalAuthFingerprint(String fingerprint) {
        this.wawelLocalAuthFingerprint = fingerprint;
    }

    @Override
    public String getWawelLocalAuthApiRoot() {
        return wawelLocalAuthApiRoot;
    }

    @Override
    public void setWawelLocalAuthApiRoot(String apiRoot) {
        this.wawelLocalAuthApiRoot = apiRoot;
    }

    @Override
    public String getWawelLocalAuthPublicKeyBase64() {
        return wawelLocalAuthPublicKeyBase64;
    }

    @Override
    public void setWawelLocalAuthPublicKeyBase64(String publicKeyBase64) {
        this.wawelLocalAuthPublicKeyBase64 = publicKeyBase64;
    }

    @Override
    public String getWawelOriginalServerIp() {
        return wawelOriginalServerIp;
    }

    @Override
    public void setWawelOriginalServerIp(String originalServerIp) {
        this.wawelOriginalServerIp = originalServerIp;
    }

    @Override
    public ServerCapabilities getWawelCapabilities() {
        return wawelCapabilities;
    }

    @Override
    public void setWawelCapabilities(ServerCapabilities capabilities) {
        this.wawelCapabilities = capabilities != null ? capabilities : ServerCapabilities.empty();
    }

    @Inject(method = "getNBTCompound", at = @At("RETURN"))
    private void wawelauth$saveNbt(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound nbt = cir.getReturnValue();
        if (wawelAccountId >= 0) {
            nbt.setLong("wawelAccountId", wawelAccountId);
        }
        if (wawelProviderName != null) {
            nbt.setString("wawelProviderName", wawelProviderName);
        }
        if (wawelLocalAuthFingerprint != null) {
            nbt.setString("wawelLocalAuthFingerprint", wawelLocalAuthFingerprint);
        }
        if (wawelLocalAuthApiRoot != null) {
            nbt.setString("wawelLocalAuthApiRoot", wawelLocalAuthApiRoot);
        }
        if (wawelLocalAuthPublicKeyBase64 != null) {
            nbt.setString("wawelLocalAuthPublicKeyBase64", wawelLocalAuthPublicKeyBase64);
        }
        if (wawelOriginalServerIp != null) {
            nbt.setString("wawelOriginalServerIp", wawelOriginalServerIp);
        }
    }

    @Inject(method = "getServerDataFromNBTCompound", at = @At("RETURN"))
    private static void wawelauth$loadNbt(NBTTagCompound nbt, CallbackInfoReturnable<ServerData> cir) {
        IServerDataExt ext = (IServerDataExt) cir.getReturnValue();
        if (nbt.hasKey("wawelAccountId")) {
            ext.setWawelAccountId(nbt.getLong("wawelAccountId"));
        }
        if (nbt.hasKey("wawelProviderName")) {
            ext.setWawelProviderName(nbt.getString("wawelProviderName"));
        }
        if (nbt.hasKey("wawelLocalAuthFingerprint")) {
            ext.setWawelLocalAuthFingerprint(nbt.getString("wawelLocalAuthFingerprint"));
        }
        if (nbt.hasKey("wawelLocalAuthApiRoot")) {
            ext.setWawelLocalAuthApiRoot(nbt.getString("wawelLocalAuthApiRoot"));
        }
        if (nbt.hasKey("wawelLocalAuthPublicKeyBase64")) {
            ext.setWawelLocalAuthPublicKeyBase64(nbt.getString("wawelLocalAuthPublicKeyBase64"));
        }
        if (nbt.hasKey("wawelOriginalServerIp")) {
            ext.setWawelOriginalServerIp(nbt.getString("wawelOriginalServerIp"));
        }
    }

    @Inject(method = "func_152583_a", at = @At("RETURN")) // ServerData.copyFrom
    private void wawelauth$copyFrom(ServerData other, CallbackInfo ci) {
        IServerDataExt otherExt = (IServerDataExt) other;
        this.wawelAccountId = otherExt.getWawelAccountId();
        this.wawelProviderName = otherExt.getWawelProviderName();
        this.wawelLocalAuthFingerprint = otherExt.getWawelLocalAuthFingerprint();
        this.wawelLocalAuthApiRoot = otherExt.getWawelLocalAuthApiRoot();
        this.wawelLocalAuthPublicKeyBase64 = otherExt.getWawelLocalAuthPublicKeyBase64();
        this.wawelOriginalServerIp = otherExt.getWawelOriginalServerIp();
        this.wawelCapabilities = otherExt.getWawelCapabilities();
    }
}
