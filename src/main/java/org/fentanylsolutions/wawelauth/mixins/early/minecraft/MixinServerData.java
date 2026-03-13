package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.NBTTagCompound;

import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;
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
    private boolean wawelServerProxyEnabled;

    @Unique
    private ProviderProxyType wawelServerProxyType = ProviderProxyType.SOCKS;

    @Unique
    private String wawelServerProxyHost;

    @Unique
    private Integer wawelServerProxyPort;

    @Unique
    private String wawelServerProxyUsername;

    @Unique
    private String wawelServerProxyPassword;

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
    public ProviderProxySettings getWawelServerProxySettings() {
        ProviderProxySettings settings = new ProviderProxySettings();
        settings.setEnabled(wawelServerProxyEnabled);
        settings.setType(wawelServerProxyType != null ? wawelServerProxyType : ProviderProxyType.SOCKS);
        settings.setHost(wawelServerProxyHost);
        settings.setPort(wawelServerProxyPort);
        settings.setUsername(wawelServerProxyUsername);
        settings.setPassword(wawelServerProxyPassword);
        return settings;
    }

    @Override
    public void setWawelServerProxySettings(ProviderProxySettings settings) {
        if (settings == null) {
            wawelServerProxyEnabled = false;
            wawelServerProxyType = ProviderProxyType.SOCKS;
            wawelServerProxyHost = null;
            wawelServerProxyPort = null;
            wawelServerProxyUsername = null;
            wawelServerProxyPassword = null;
            return;
        }

        wawelServerProxyEnabled = settings.isEnabled();
        wawelServerProxyType = settings.getType() != null ? settings.getType() : ProviderProxyType.SOCKS;
        wawelServerProxyHost = settings.getHost();
        wawelServerProxyPort = settings.getPort();
        wawelServerProxyUsername = settings.getUsername();
        wawelServerProxyPassword = settings.getPassword();
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
        if (wawelServerProxyEnabled) {
            nbt.setBoolean("wawelServerProxyEnabled", true);
        }
        if (wawelServerProxyType != null) {
            nbt.setString("wawelServerProxyType", wawelServerProxyType.name());
        }
        if (wawelServerProxyHost != null) {
            nbt.setString("wawelServerProxyHost", wawelServerProxyHost);
        }
        if (wawelServerProxyPort != null) {
            nbt.setInteger("wawelServerProxyPort", wawelServerProxyPort.intValue());
        }
        if (wawelServerProxyUsername != null) {
            nbt.setString("wawelServerProxyUsername", wawelServerProxyUsername);
        }
        if (wawelServerProxyPassword != null) {
            nbt.setString("wawelServerProxyPassword", wawelServerProxyPassword);
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
        if (nbt.hasKey("wawelServerProxyEnabled") || nbt.hasKey("wawelServerProxyType")
            || nbt.hasKey("wawelServerProxyHost")
            || nbt.hasKey("wawelServerProxyPort")
            || nbt.hasKey("wawelServerProxyUsername")
            || nbt.hasKey("wawelServerProxyPassword")) {
            ProviderProxySettings settings = new ProviderProxySettings();
            settings.setEnabled(nbt.getBoolean("wawelServerProxyEnabled"));
            if (nbt.hasKey("wawelServerProxyType")) {
                try {
                    settings.setType(ProviderProxyType.valueOf(nbt.getString("wawelServerProxyType")));
                } catch (Exception ignored) {
                    settings.setType(ProviderProxyType.SOCKS);
                }
            } else {
                settings.setType(ProviderProxyType.SOCKS);
            }
            if (nbt.hasKey("wawelServerProxyHost")) {
                settings.setHost(nbt.getString("wawelServerProxyHost"));
            }
            if (nbt.hasKey("wawelServerProxyPort")) {
                settings.setPort(Integer.valueOf(nbt.getInteger("wawelServerProxyPort")));
            }
            if (nbt.hasKey("wawelServerProxyUsername")) {
                settings.setUsername(nbt.getString("wawelServerProxyUsername"));
            }
            if (nbt.hasKey("wawelServerProxyPassword")) {
                settings.setPassword(nbt.getString("wawelServerProxyPassword"));
            }
            ext.setWawelServerProxySettings(settings);
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
        this.setWawelServerProxySettings(otherExt.getWawelServerProxySettings());
        this.wawelCapabilities = otherExt.getWawelCapabilities();
    }
}
