package org.fentanylsolutions.wawelauth.client.gui;

import java.util.UUID;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelFaceRendererClient;
import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.api.internal.TextureRequest;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ModularUI widget that renders a player face at 8x8 pixels.
 * Uses {@link WawelTextureResolver} to resolve skins, no TabFaces dependency.
 */
@SideOnly(Side.CLIENT)
public class FaceWidget extends Widget<FaceWidget> {

    private final UUID profileUuid;
    private final String displayName;
    private final String providerName;
    private float alpha = 1.0f;

    public FaceWidget(String displayName, UUID profileUuid) {
        this(displayName, profileUuid, null);
    }

    public FaceWidget(String displayName, UUID profileUuid, String providerName) {
        this.displayName = displayName;
        this.profileUuid = profileUuid;
        this.providerName = providerName;
    }

    public FaceWidget alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        WawelClient client = WawelClient.instance();
        ResourceLocation skin;
        if (client != null && profileUuid != null) {
            if (providerName != null && !providerName.trim()
                .isEmpty()) {
                skin = client.getTextureResolver()
                    .getSkin(profileUuid, displayName, providerName, TextureRequest.DEFAULT);
            } else {
                skin = client.getTextureResolver()
                    .getSkin(profileUuid, displayName, TextureRequest.DEFAULT);
            }
        } else {
            skin = WawelTextureResolver.getDefaultSkin();
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.disableStandardItemLighting();

            WawelFaceRendererClient.drawFace(skin, 0, 0, alpha);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }
}
