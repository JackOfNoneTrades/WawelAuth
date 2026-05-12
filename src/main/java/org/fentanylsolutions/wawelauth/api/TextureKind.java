package org.fentanylsolutions.wawelauth.api;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;

enum TextureKind {

    SKIN(MinecraftProfileTexture.Type.SKIN, "skin", "offline_skins"),
    CAPE(MinecraftProfileTexture.Type.CAPE, "cape", "offline_capes");

    final MinecraftProfileTexture.Type profileType;
    final String label;
    final String offlinePathPrefix;

    TextureKind(MinecraftProfileTexture.Type profileType, String label, String offlinePathPrefix) {
        this.profileType = profileType;
        this.label = label;
        this.offlinePathPrefix = offlinePathPrefix;
    }

    boolean isSkin() {
        return this == SKIN;
    }
}
