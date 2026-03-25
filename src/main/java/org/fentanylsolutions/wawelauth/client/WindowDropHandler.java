package org.fentanylsolutions.wawelauth.client;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;

import org.fentanylsolutions.fentlib.util.drop.DropListener;
import org.fentanylsolutions.fentlib.util.drop.WindowDropTarget;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.gui.AccountManagerScreen;
import org.fentanylsolutions.wawelauth.client.gui.ServerDropScreen;
import org.fentanylsolutions.wawelauth.client.gui.TextureDropOverlay;

import com.cleanroommc.modularui.api.IMuiScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * WawelAuth-specific drag-and-drop handler, registered as a {@link DropListener}
 * on FentLib's {@link WindowDropTarget}.
 * <p>
 * Two modes:
 * <ul>
 * <li><b>Server add</b> — {@code wawelauth-server://} text drops on main menu or multiplayer.</li>
 * <li><b>Texture file</b> — file drops on the account manager when a skin preview is active.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class WindowDropHandler implements DropListener {

    private static final String URI_SCHEME = "wawelauth-server";
    private static final String URI_ACTION = "add";

    private volatile DragMode dragMode = DragMode.NONE;

    public static void register() {
        WindowDropTarget.register();
        WindowDropTarget.addListener(new WindowDropHandler());
    }

    @Override
    public void onDragBegin() {
        dragMode = DragMode.NONE;
        if (isOnTextureScreen()) {
            TextureDropOverlay.show();
        } else if (isOnServerAddScreen()) {
            ServerDropScreen.showHint();
        }
    }

    @Override
    public void onDropText(String text) {
        if (dragMode == DragMode.NONE) dragMode = DragMode.SERVER_ADD;
    }

    @Override
    public void onDropFile(String filePath, float sdlX, float sdlY) {
        if (dragMode == DragMode.NONE) dragMode = DragMode.TEXTURE;
        TextureDropOverlay.setDroppedFile(filePath);
        WawelAuth.LOG.info("[WindowDropHandler] DROP_FILE x={} y={} file={}", sdlX, sdlY, filePath);
    }

    @Override
    public void onDragPosition(float sdlX, float sdlY) {
        TextureDropOverlay.updateDropPosition(sdlX, sdlY);
    }

    @Override
    public void onDragComplete(WindowDropTarget.DropResult result) {
        DragMode mode = dragMode;
        dragMode = DragMode.NONE;

        if (mode == DragMode.TEXTURE || isOnTextureScreen()) {
            TextureDropOverlay.complete();
            ServerDropScreen.dismissHint();
            return;
        }

        String text = result.getText();
        ServerDropScreen.dismissHintThenRun(() -> {
            if (text == null || text.isEmpty()) return;
            if (!isOnServerAddScreen()) return;

            ServerAddRequest request = parseServerUri(text);
            if (request == null) {
                WawelAuth.LOG.debug("[WindowDropHandler] Ignored non-wawelauth drop: {}", text);
                return;
            }

            WawelAuth.LOG
                .info("[WindowDropHandler] Server drop: name='{}', address='{}'", request.name, request.address);
            ServerDropScreen.showConfirmation(request.name, request.address);
        });
    }

    // -- Screen detection --

    private static boolean isOnServerAddScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return false;
        if (screen instanceof GuiMainMenu || screen instanceof GuiMultiplayer) return true;
        if (screen instanceof IMuiScreen muiScreen) {
            GuiScreen parent = muiScreen.getScreen()
                .getContext()
                .getParentScreen();
            return parent instanceof GuiMainMenu || parent instanceof GuiMultiplayer;
        }
        return false;
    }

    private static boolean isOnTextureScreen() {
        AccountManagerScreen ams = TextureDropOverlay.findAccountManagerScreen(Minecraft.getMinecraft());
        return ams != null && ams.isTexturePreviewActive();
    }

    // -- URI parsing --

    static ServerAddRequest parseServerUri(String text) {
        try {
            URI uri = new URI(text.trim());
            if (!URI_SCHEME.equals(uri.getScheme())) return null;
            if (!URI_ACTION.equals(uri.getAuthority())) return null;

            Map<String, String> params = parseQuery(uri.getRawQuery());
            String name = params.get("name");
            String address = params.get("address");
            if (name == null || name.isEmpty() || address == null || address.isEmpty()) return null;

            return new ServerAddRequest(name, address);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return result;
        try {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                String value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                result.put(key, value);
            }
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always supported
        }
        return result;
    }

    // -- Types --

    private enum DragMode {
        NONE,
        SERVER_ADD,
        TEXTURE
    }

    static final class ServerAddRequest {

        final String name;
        final String address;

        ServerAddRequest(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }
}
