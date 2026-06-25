package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;

final class AccountManagerScreenState {

    ClientProvider selectedProvider;
    ClientAccount selectedAccount;

    long localAuthFingerprintCopiedUntilMs;
    long profileUuidCopiedUntilMs;

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
    String textureUploadStatus = "";
    long textureFilePickerRequestId;
    boolean pendingTextureUploadSkin;
    File pendingTextureUploadFile;
    boolean pendingTextureUploadSlim;
    TextureType pendingTextureResetType;

    Map<String, Boolean> registerCapabilityByProvider = new HashMap<>();
    Set<String> registerCapabilityProbeInFlight = new HashSet<>();
    long nextStatusUiRefreshAtMs;

    void resetForBuild() {
        this.localAuthFingerprintCopiedUntilMs = 0L;
        this.profileUuidCopiedUntilMs = 0L;
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
        this.textureUploadStatus = "";
        this.textureFilePickerRequestId++;
        this.pendingTextureUploadSkin = true;
        this.pendingTextureUploadFile = null;
        this.pendingTextureUploadSlim = true;
        this.pendingTextureResetType = null;
        this.registerCapabilityByProvider = new HashMap<>();
        this.registerCapabilityProbeInFlight = new HashSet<>();
        this.nextStatusUiRefreshAtMs = 0L;
    }
}
