package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler;
import org.fentanylsolutions.fentlib.util.drop.WindowDropTarget;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
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
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 90;
    private static final int ZONE_WIDTH = 100;
    private static final int ZONE_HEIGHT = 50;
    private static final int ZONE_GAP = 8;
    private static final int ZONE_Y_OFFSET = 30;

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

    public static boolean isOpen() {
        return overlayOpen;
    }

    public static void dismiss() {
        overlayOpen = false;
        pendingFilePath = null;
        hoveredZone = 0;
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

    public static void complete(String filePath, float dropSdlX, float dropSdlY) {
        overlayOpen = false;
        if (filePath == null || filePath.isEmpty()) {
            filePath = pendingFilePath;
        }
        pendingFilePath = null;
        int zone = resolveDropZone(dropSdlX, dropSdlY);
        if (zone == 0) {
            zone = hoveredZone;
        }
        hoveredZone = 0;

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
            if (!ams.canAcceptTextureDrop()) {
                return;
            }

            ams.acceptDroppedTextureFile(file, isSkin);
            WawelAuth.LOG.info("[TextureDropOverlay] Dropped {} as {}", file.getName(), isSkin ? "skin" : "cape");
        });
    }

    private static boolean isInsideArea(Area area, float guiX, float guiY) {
        return guiX >= area.x && guiX < area.ex() && guiY >= area.y && guiY < area.ey();
    }

    private static int resolveDropZone(float dropSdlX, float dropSdlY) {
        float[] gui = WindowDropTarget.sdlToGuiCoords(dropSdlX, dropSdlY);
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int panelX = (sr.getScaledWidth() - PANEL_WIDTH) / 2;
        int panelY = (sr.getScaledHeight() - PANEL_HEIGHT) / 2;
        int skinX = panelX + (PANEL_WIDTH - ZONE_WIDTH * 2 - ZONE_GAP) / 2;
        int capeX = skinX + ZONE_WIDTH + ZONE_GAP;
        int zoneY = panelY + ZONE_Y_OFFSET;

        if (isInsideRect(gui[0], gui[1], skinX, zoneY, ZONE_WIDTH, ZONE_HEIGHT)) {
            return 1;
        }
        if (isInsideRect(gui[0], gui[1], capeX, zoneY, ZONE_WIDTH, ZONE_HEIGHT)) {
            return 2;
        }
        return 0;
    }

    private static boolean isInsideRect(float x, float y, int rectX, int rectY, int width, int height) {
        return x >= rectX && x < rectX + width && y >= rectY && y < rectY + height;
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
            openParentOnClose(true);
        }

        @Override
        public ModularPanel buildUI(ModularGuiContext context) {
            ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME)
                .size(PANEL_WIDTH, PANEL_HEIGHT)
                .align(Alignment.Center);
            WawelAuthStyle.dialog(panel);

            skinZoneWidget = dropZone(1, "wawelauth.gui.drop.zone_skin");
            capeZoneWidget = dropZone(2, "wawelauth.gui.drop.zone_cape");

            panel.child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.drop.texture_title")).widthRel(1.0f)
                            .height(14)
                            .alignment(Alignment.Center)
                            .color(WawelAuthStyle.THEME_LIGHTER))
                    .child(new Widget<>().size(1, 6))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(ZONE_HEIGHT)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(skinZoneWidget)
                            .child(new Widget<>().size(ZONE_GAP, ZONE_HEIGHT))
                            .child(capeZoneWidget)));

            return panel;
        }

        private Widget<?> dropZone(int zone, String labelKey) {
            IDrawable background = (ctx, x, y, width, height, widgetTheme) -> {
                int accent = WawelAuthStyle.accent();
                int color = hoveredZone == zone ? withAlpha(accent, 0x66) : withAlpha(accent, 0x22);
                Gui.drawRect(x, y, x + width, y + height, color);
                Gui.drawRect(x, y + height - 1, x + width, y + height, accent);
            };
            return new Column().size(ZONE_WIDTH, ZONE_HEIGHT)
                .background(background)
                .disableHoverThemeBackground(true)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .child(
                    new TextWidget<>(GuiText.key(labelKey))
                        .color(
                            () -> hoveredZone == zone ? WawelAuthStyle.TEXT_PRIMARY : WawelAuthStyle.TEXT_BUTTON_IDLE)
                        .alignment(Alignment.Center)
                        .widthRel(1.0f)
                        .height(14));
        }

        private static int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | (alpha << 24);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!overlayOpen) {
                getMainPanel().closeIfOpen();
                return;
            }
            if (!Mouse.isInsideWindow()) {
                dismiss();
                return;
            }

            // Hit-test SDL cursor against zone widget bounds
            float[] gui = WindowDropTarget.sdlToGuiCoords(sdlX, sdlY);
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
