package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.fentanylsolutions.wawelauth.WawelAuth;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Overlay shown during a file drag onto the account manager.
 * Displays a centered panel with two drop zones (Skin / Cape).
 * <p>
 * During an OS-level drag, SDL does not update the game's mouse cursor,
 * so hover detection converts SDL drop-position coordinates into GUI space
 * and hit-tests against the actual zone widget bounds.
 */
@SideOnly(Side.CLIENT)
public final class TextureDropOverlay {

    private static final String PANEL_NAME = "wawelauth_texture_drop";

    private static final int COLOR_ZONE_NORMAL = 0x44AAAAAA;
    private static final int COLOR_ZONE_HOVER = 0xAA55FF55;

    private static volatile boolean overlayOpen;
    private static volatile String pendingFilePath;

    /** SDL logical-point coordinates of the cursor during drag. */
    private static volatile float sdlX;
    private static volatile float sdlY;

    /**
     * Zone hover state, updated each frame from SDL coords vs widget bounds.
     * 0 = neither, 1 = skin, 2 = cape.
     */
    private static volatile int hoveredZone;

    private TextureDropOverlay() {}

    public static void show() {
        if (overlayOpen) return;
        pendingFilePath = null;
        hoveredZone = 0;
        overlayOpen = true;
        ClientGUI.open(new OverlayScreen());
    }

    public static void dismiss() {
        overlayOpen = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof com.cleanroommc.modularui.api.IMuiScreen muiScreen) {
            ModularPanel mainPanel = muiScreen.getScreen()
                .getMainPanel();
            if (mainPanel != null && PANEL_NAME.equals(mainPanel.getName())) {
                mainPanel.closeIfOpen();
            }
        }
    }

    /** Called from SDL event thread with coordinates in logical points. */
    public static void updateDropPosition(float x, float y) {
        sdlX = x;
        sdlY = y;
    }

    /** Called from SDL event thread when a file is dropped. */
    public static void setDroppedFile(String path) {
        pendingFilePath = path;
    }

    public static void complete() {
        overlayOpen = false;
        String filePath = pendingFilePath;
        pendingFilePath = null;
        int zone = hoveredZone;

        dismiss();

        if (filePath == null || filePath.isEmpty()) return;
        if (zone == 0) {
            WawelAuth.LOG.info("[TextureDropOverlay] Dropped outside both zones, ignoring");
            return;
        }

        boolean isSkin = zone == 1;
        File file = new File(filePath);

        GuiTransitionScheduler.nextTick(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            AccountManagerScreen ams = findAccountManagerScreen(mc);
            if (ams == null) {
                WawelAuth.LOG.warn("[TextureDropOverlay] AccountManagerScreen not found after drop");
                return;
            }

            ams.acceptDroppedTextureFile(file, isSkin);
            WawelAuth.LOG.info("[TextureDropOverlay] Dropped {} as {}", file.getName(), isSkin ? "skin" : "cape");
        });
    }

    /**
     * Convert SDL logical-point coordinates to Minecraft GUI-scaled coordinates.
     * SDL coords: 0..logicalWindowWidth (points)
     * GUI coords: 0..scaledWidth (Minecraft GUI units)
     */
    private static float[] sdlToGui(float sx, float sy) {
        float pixelScale = org.lwjgl.opengl.Display.getPixelScaleFactor();
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        // SDL logical -> framebuffer pixels -> GUI scaled
        float fbX = sx * pixelScale;
        float fbY = sy * pixelScale;
        float guiX = fbX / sr.getScaleFactor();
        float guiY = fbY / sr.getScaleFactor();
        return new float[] { guiX, guiY };
    }

    private static boolean isInsideArea(Area area, float guiX, float guiY) {
        return guiX >= area.x && guiX < area.ex() && guiY >= area.y && guiY < area.ey();
    }

    public static AccountManagerScreen findAccountManagerScreen(Minecraft mc) {
        if (mc.currentScreen instanceof com.cleanroommc.modularui.api.IMuiScreen muiScreen) {
            if (muiScreen.getScreen() instanceof AccountManagerScreen ams) {
                return ams;
            }
            var parentScreen = muiScreen.getScreen()
                .getContext()
                .getParentScreen();
            if (parentScreen instanceof com.cleanroommc.modularui.api.IMuiScreen parentMui) {
                if (parentMui.getScreen() instanceof AccountManagerScreen ams) {
                    return ams;
                }
            }
        }
        return null;
    }

    // ── Overlay screen ──────────────────────────────────────────────────

    private static final class OverlayScreen extends ParentAwareModularScreen {

        private Widget<?> skinZoneWidget;
        private Widget<?> capeZoneWidget;

        OverlayScreen() {
            super("wawelauth");
        }

        @Override
        public ModularPanel buildUI(ModularGuiContext context) {
            ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME)
                .size(240, 90)
                .align(Alignment.Center);

            Rectangle skinBg = new Rectangle() {

                @Override
                public void draw(GuiContext ctx, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                    setColor(hoveredZone == 1 ? COLOR_ZONE_HOVER : COLOR_ZONE_NORMAL);
                    super.draw(ctx, x, y, width, height, widgetTheme);
                }
            };
            Rectangle capeBg = new Rectangle() {

                @Override
                public void draw(GuiContext ctx, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                    setColor(hoveredZone == 2 ? COLOR_ZONE_HOVER : COLOR_ZONE_NORMAL);
                    super.draw(ctx, x, y, width, height, widgetTheme);
                }
            };

            skinZoneWidget = new Column().size(100, 50)
                .background(skinBg)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.drop.zone_skin")).color(0xFFFFFFFF)
                        .alignment(Alignment.Center)
                        .widthRel(1.0f)
                        .height(14));

            capeZoneWidget = new Column().size(100, 50)
                .background(capeBg)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.drop.zone_cape")).color(0xFFFFFFFF)
                        .alignment(Alignment.Center)
                        .widthRel(1.0f)
                        .height(14));

            panel.child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.drop.texture_title")).widthRel(1.0f)
                            .height(14)
                            .alignment(Alignment.Center))
                    .child(new Widget<>().size(1, 4))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(50)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(skinZoneWidget)
                            .child(new Widget<>().size(8, 50))
                            .child(capeZoneWidget)));

            return panel;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!overlayOpen) {
                getMainPanel().closeIfOpen();
                return;
            }

            // Hit-test SDL cursor against zone widget bounds
            float[] gui = sdlToGui(sdlX, sdlY);
            if (skinZoneWidget != null && isInsideArea(skinZoneWidget.getArea(), gui[0], gui[1])) {
                hoveredZone = 1;
            } else if (capeZoneWidget != null && isInsideArea(capeZoneWidget.getArea(), gui[0], gui[1])) {
                hoveredZone = 2;
            } else {
                hoveredZone = 0;
            }
        }
    }
}
