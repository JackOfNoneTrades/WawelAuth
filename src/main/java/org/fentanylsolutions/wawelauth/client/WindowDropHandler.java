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

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.gui.AccountManagerScreen;
import org.fentanylsolutions.wawelauth.client.gui.ServerDropScreen;
import org.fentanylsolutions.wawelauth.client.gui.TextureDropOverlay;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_DropEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilterI;

import com.cleanroommc.modularui.api.IMuiScreen;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * Handles drag-and-drop onto the game window using SDL3's event watch system
 * (available via lwjgl3ify).
 * <p>
 * Two modes:
 * <ul>
 * <li><b>Server add</b> — {@code wawelauth-server://} text drops on main menu or multiplayer.</li>
 * <li><b>Texture file</b> — file drops on the account manager when a skin preview is active.</li>
 * </ul>
 */
@Lwjgl3Aware
@SideOnly(Side.CLIENT)
public final class WindowDropHandler {

    private static final String URI_SCHEME = "wawelauth-server";
    private static final String URI_ACTION = "add";

    private static final WindowDropHandler INSTANCE = new WindowDropHandler();
    private static volatile boolean registered;
    private boolean watchInstalled;

    /** Strong reference to prevent GC while SDL holds the native pointer. */
    private SDL_EventFilterI eventWatch;

    // Per-drag session state (written from SDL thread, read on main thread)
    private volatile String pendingDropText;
    private volatile String pendingDropFile;
    private volatile float lastDropX;
    private volatile float lastDropY;

    /** Which handler is active for the current drag session. */
    private volatile DragMode dragMode = DragMode.NONE;

    private WindowDropHandler() {}

    public static synchronized void register() {
        if (registered) return;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!watchInstalled) {
            installEventWatch();
        }
    }

    private void installEventWatch() {
        eventWatch = (userdata, eventPtr) -> {
            SDL_Event sdlEvent = SDL_Event.create(eventPtr);
            int type = sdlEvent.type();

            if (type == SDLEvents.SDL_EVENT_DROP_BEGIN) {
                pendingDropText = null;
                pendingDropFile = null;
                dragMode = DragMode.NONE;
                scheduleOnMainThread(this::onDragBegin);
            } else if (type == SDLEvents.SDL_EVENT_DROP_TEXT) {
                SDL_DropEvent drop = sdlEvent.drop();
                pendingDropText = drop.dataString();
                if (dragMode == DragMode.NONE) dragMode = DragMode.SERVER_ADD;
            } else if (type == SDLEvents.SDL_EVENT_DROP_FILE) {
                SDL_DropEvent drop = sdlEvent.drop();
                pendingDropFile = drop.dataString();
                lastDropX = drop.x();
                lastDropY = drop.y();
                if (dragMode == DragMode.NONE) dragMode = DragMode.TEXTURE;
                TextureDropOverlay.setDroppedFile(pendingDropFile);
                WawelAuth.LOG
                    .info("[WindowDropHandler] DROP_FILE x={} y={} file={}", drop.x(), drop.y(), pendingDropFile);
            } else if (type == SDLEvents.SDL_EVENT_DROP_POSITION) {
                SDL_DropEvent drop = sdlEvent.drop();
                lastDropX = drop.x();
                lastDropY = drop.y();
                TextureDropOverlay.updateDropPosition(drop.x(), drop.y());
            } else if (type == SDLEvents.SDL_EVENT_DROP_COMPLETE) {
                WawelAuth.LOG.info(
                    "[WindowDropHandler] DROP_COMPLETE lastDropX={} lastDropY={} dragMode={}",
                    lastDropX,
                    lastDropY,
                    dragMode);
                DragMode mode = dragMode;
                String text = pendingDropText;
                pendingDropText = null;
                pendingDropFile = null;
                dragMode = DragMode.NONE;
                scheduleOnMainThread(() -> onDragComplete(mode, text));
            }

            return true;
        };

        SDLEvents.SDL_AddEventWatch(eventWatch, 0L);
        watchInstalled = true;
        WawelAuth.LOG.info("[WindowDropHandler] SDL drop event watch installed");
    }

    // -- Drag begin --

    private void onDragBegin() {
        // Texture screen check first — it's more specific, and its parent
        // is often the multiplayer menu which would also match server-add.
        if (isOnTextureScreen()) {
            TextureDropOverlay.show();
        } else if (isOnServerAddScreen()) {
            ServerDropScreen.showHint();
        }
    }

    // -- Drag complete --

    private void onDragComplete(DragMode mode, String text) {
        if (mode == DragMode.TEXTURE || isOnTextureScreen()) {
            TextureDropOverlay.complete();
            // Also dismiss server hint in case it was open
            ServerDropScreen.dismissHint();
            return;
        }

        // Server add flow
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

    private static void scheduleOnMainThread(Runnable action) {
        Minecraft.getMinecraft()
            .func_152344_a(action); // addScheduledTask
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
