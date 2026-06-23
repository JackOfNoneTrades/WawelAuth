package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.multiplayer.ServerData;

import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

final class AccountManagerScreenState {

    ClientProvider selectedProvider;
    ClientAccount selectedAccount;

    ServerData focusedLocalServerData;
    ServerCapabilities focusedLocalCapabilities;
    String focusedLocalStatusText = "";

    ServerCapabilities connectedServerCapabilities;

    long pendingRemoveAccountId = -1L;
    String pendingRemoveAccountName;
    String pendingProviderSettingsName;
    String pendingProviderProxyName;
    String pendingProviderDeleteName;
    long pendingCredentialDeleteAccountId = -1L;
    String pendingCredentialDeleteAccountName;
    String pendingCredentialDeletePassword;

    boolean texturePathDialogForSkin;
    String texturePathDialogInitialPath;
    File selectedSkinFile;
    File selectedCapeFile;
    boolean skinUploadSlim;
    String textureSelectionStatus = "";
    String textureUploadStatus = "";

    Map<String, Boolean> registerCapabilityByProvider = new HashMap<>();
    Set<String> registerCapabilityProbeInFlight = new HashSet<>();
    long nextStatusUiRefreshAtMs;

    void resetForBuild(ServerData focusedLocalServerData, ServerCapabilities focusedLocalCapabilities) {
        this.focusedLocalServerData = focusedLocalServerData;
        this.focusedLocalCapabilities = focusedLocalCapabilities;
        this.focusedLocalStatusText = "";
        this.connectedServerCapabilities = null;
        this.pendingRemoveAccountId = -1L;
        this.pendingRemoveAccountName = null;
        this.pendingProviderSettingsName = null;
        this.pendingProviderProxyName = null;
        this.pendingProviderDeleteName = null;
        this.pendingCredentialDeleteAccountId = -1L;
        this.pendingCredentialDeleteAccountName = null;
        this.pendingCredentialDeletePassword = null;
        this.texturePathDialogForSkin = false;
        this.texturePathDialogInitialPath = null;
        this.selectedSkinFile = null;
        this.selectedCapeFile = null;
        this.skinUploadSlim = false;
        this.textureSelectionStatus = "";
        this.textureUploadStatus = "";
        this.registerCapabilityByProvider = new HashMap<>();
        this.registerCapabilityProbeInFlight = new HashSet<>();
        this.nextStatusUiRefreshAtMs = 0L;
    }
}
