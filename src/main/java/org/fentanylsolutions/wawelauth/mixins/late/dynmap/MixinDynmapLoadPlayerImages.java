package org.fentanylsolutions.wawelauth.mixins.late.dynmap;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;

import org.dynmap.MapType.ImageFormat;
import org.dynmap.PlayerFaces;
import org.dynmap.debug.Debug;
import org.dynmap.storage.MapStorage;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.ImageIOManager;
import org.fentanylsolutions.wawelauth.api.WawelFaceRendererServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "org.dynmap.PlayerFaces$LoadPlayerImages", remap = false)
public abstract class MixinDynmapLoadPlayerImages {

    @Shadow(remap = false)
    @Final
    public String playername;

    @Shadow(remap = false)
    @Final
    public String playerskinurl;

    @Shadow(remap = false)
    @Final
    public PlayerFaces this$0;

    /**
     * Uses WawelAuth's HD-safe face renderer while keeping Dynmap's own face/body storage paths.
     *
     * @author Codex
     * @reason Dynmap's fixed 8x8 sampling breaks on HD skins, and the frontend can downscale larger intrinsic PNGs.
     */
    @Overwrite(remap = false)
    public void run() {
        MapStorage storage = this.this$0.storage;
        if (storage == null) {
            return;
        }

        boolean has8x8 = storage.hasPlayerFaceImage(this.playername, PlayerFaces.FaceType.FACE_8X8);
        boolean has16x16 = storage.hasPlayerFaceImage(this.playername, PlayerFaces.FaceType.FACE_16X16);
        boolean has32x32 = storage.hasPlayerFaceImage(this.playername, PlayerFaces.FaceType.FACE_32X32);
        boolean hasBody = storage.hasPlayerFaceImage(this.playername, PlayerFaces.FaceType.BODY_32X32);
        boolean missingAny = !(has8x8 && has16x16 && has32x32 && hasBody);

        AccessorPlayerFaces accessor = (AccessorPlayerFaces) (Object) this.this$0;
        boolean refreshskins = accessor.wawelauth$getRefreshskins();
        if (!refreshskins && !missingAny) {
            return;
        }

        BufferedImage skin = null;
        try {
            if (accessor.wawelauth$getFetchskins()) {
                skin = loadSkin(accessor.wawelauth$getSkinurl(), this.playerskinurl, this.playername);
            }
            if (skin == null) {
                skin = loadDefaultSkin();
            }
            if (skin == null) {
                return;
            }
            if (skin.getWidth() < 64 || skin.getHeight() < 32) {
                return;
            }

            BufferedImage face8 = null;
            BufferedImage faceLarge = null;
            BufferedImage body32 = null;
            try {
                if (refreshskins || !has8x8 || !hasBody) {
                    face8 = WawelFaceRendererServer.renderFace(skin, 8);
                }
                if (refreshskins || !has16x16 || !has32x32) {
                    faceLarge = WawelFaceRendererServer.renderFace(skin, 64);
                }
                if (refreshskins || !hasBody) {
                    body32 = renderBody32(skin, face8);
                }

                if (refreshskins || !has8x8) {
                    write(storage, this.playername, PlayerFaces.FaceType.FACE_8X8, face8);
                }
                if (refreshskins || !has16x16) {
                    write(storage, this.playername, PlayerFaces.FaceType.FACE_16X16, faceLarge);
                }
                if (refreshskins || !has32x32) {
                    write(storage, this.playername, PlayerFaces.FaceType.FACE_32X32, faceLarge);
                }
                if (refreshskins || !hasBody) {
                    write(storage, this.playername, PlayerFaces.FaceType.BODY_32X32, body32);
                }
            } finally {
                if (face8 != null) {
                    face8.flush();
                }
                if (faceLarge != null) {
                    faceLarge.flush();
                }
                if (body32 != null) {
                    body32.flush();
                }
            }
        } finally {
            if (skin != null) {
                skin.flush();
            }
        }
    }

    private static BufferedImage loadSkin(String configuredSkinUrl, String playerskinurl, String playername) {
        try {
            URL url = resolveSkinUrl(configuredSkinUrl, playerskinurl, playername);
            if (url == null) {
                return null;
            }
            return javax.imageio.ImageIO.read(url);
        } catch (IOException iox) {
            Debug.debug("Error loading skin for '" + playername + "' - " + iox);
            return null;
        }
    }

    private static URL resolveSkinUrl(String configuredSkinUrl, String playerskinurl, String playername)
        throws IOException {
        if (configuredSkinUrl != null && !configuredSkinUrl.isEmpty()) {
            String encodedName = URLEncoder.encode(playername, "UTF-8");
            return new URL(configuredSkinUrl.replace("%player%", encodedName));
        }
        if (playerskinurl != null) {
            return new URL(playerskinurl);
        }
        return null;
    }

    private static BufferedImage loadDefaultSkin() {
        try (InputStream in = MixinDynmapLoadPlayerImages.class.getResourceAsStream("/char.png")) {
            if (in == null) {
                return null;
            }
            return javax.imageio.ImageIO.read(in);
        } catch (IOException iox) {
            Debug.debug("Error loading default skin - " + iox);
            return null;
        }
    }

    private static void write(MapStorage storage, String playername, PlayerFaces.FaceType faceType,
        BufferedImage image) {
        if (image == null) {
            return;
        }
        BufferOutputStream bos = ImageIOManager.imageIOEncode(image, ImageFormat.FORMAT_PNG);
        if (bos != null) {
            storage.setPlayerFaceImage(playername, faceType, bos);
        }
    }

    private static BufferedImage renderBody32(BufferedImage skin, BufferedImage face8) {
        if (skin == null || face8 == null) {
            return null;
        }

        int width = skin.getWidth();
        int height = skin.getHeight();
        if (width < 16 || height < 16) {
            return null;
        }

        boolean legacyLayout = width == height * 2;
        float uScale = width / 64.0F;
        float vScale = height / (legacyLayout ? 32.0F : 64.0F);

        BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            g.drawImage(face8, 12, 0, null);
            drawScaledRegion(g, skin, 12, 8, 20, 20, 20, 20, 28, 32, uScale, vScale);
            drawScaledRegion(g, skin, 12, 20, 16, 32, 4, 20, 8, 32, uScale, vScale);
            drawScaledRegion(g, skin, 16, 20, 20, 32, 4, 20, 8, 32, uScale, vScale);
            drawScaledRegion(g, skin, 8, 8, 12, 20, 44, 20, 48, 32, uScale, vScale);
            drawScaledRegion(g, skin, 20, 8, 24, 20, 44, 20, 48, 32, uScale, vScale);
        } finally {
            g.dispose();
        }

        return out;
    }

    private static void drawScaledRegion(Graphics2D g, BufferedImage skin, int dstX0, int dstY0, int dstX1, int dstY1,
        int srcU0, int srcV0, int srcU1, int srcV1, float uScale, float vScale) {
        int srcX0 = scaleCoord(srcU0, uScale, skin.getWidth());
        int srcY0 = scaleCoord(srcV0, vScale, skin.getHeight());
        int srcX1 = scaleCoord(srcU1, uScale, skin.getWidth());
        int srcY1 = scaleCoord(srcV1, vScale, skin.getHeight());
        if (!hasArea(srcX0, srcY0, srcX1, srcY1, skin.getWidth(), skin.getHeight())) {
            return;
        }
        g.drawImage(skin, dstX0, dstY0, dstX1, dstY1, srcX0, srcY0, srcX1, srcY1, null);
    }

    private static int scaleCoord(int uv, float scale, int limit) {
        int scaled = Math.round(uv * scale);
        if (scaled < 0) {
            return 0;
        }
        return Math.min(limit, scaled);
    }

    private static boolean hasArea(int x0, int y0, int x1, int y1, int maxWidth, int maxHeight) {
        if (x0 < 0 || y0 < 0 || x1 > maxWidth || y1 > maxHeight) {
            return false;
        }
        return x1 > x0 && y1 > y0;
    }
}
