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
import org.fentanylsolutions.wawelauth.client.gui.ServerDropScreen;
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
 * Handles drag-and-drop of {@code wawelauth-server://} URIs onto the game window
 * using SDL3's event watch system (available via lwjgl3ify).
 * <p>
 * Only active when the current screen is the main menu or multiplayer menu.
 * On drag begin, shows a hint overlay. On drop, parses the URI and prompts
 * for confirmation before adding the server.
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

    /** Text payload from a DROP_TEXT event within the current drag session. */
    private volatile String pendingDropText;
    /** Whether a drag session is currently active. */
    private volatile boolean dragActive;

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
                dragActive = true;
                scheduleOnMainThread(this::onDragBegin);
            } else if (type == SDLEvents.SDL_EVENT_DROP_TEXT) {
                SDL_DropEvent drop = sdlEvent.drop();
                pendingDropText = drop.dataString();
            } else if (type == SDLEvents.SDL_EVENT_DROP_COMPLETE) {
                dragActive = false;
                String text = pendingDropText;
                pendingDropText = null;
                scheduleOnMainThread(() -> onDragComplete(text));
            }

            return true;
        };

        SDLEvents.SDL_AddEventWatch(eventWatch, 0L);
        watchInstalled = true;
        WawelAuth.LOG.info("[WindowDropHandler] SDL drop event watch installed");
    }

    private void onDragBegin() {
        if (!isOnEligibleScreen()) return;
        ServerDropScreen.showHint();
    }

    private void onDragComplete(String text) {
        // Defer confirmation to the next tick so the hint screen fully tears down first.
        ServerDropScreen.dismissHintThenRun(() -> {
            if (text == null || text.isEmpty()) return;
            if (!isOnEligibleScreen()) return;

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

    private static boolean isOnEligibleScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return false;

        // Allow if on the main menu or multiplayer menu.
        // Also allow if a ModularUI overlay is open on top of either.
        if (screen instanceof GuiMainMenu || screen instanceof GuiMultiplayer) {
            return true;
        }
        if (screen instanceof IMuiScreen muiScreen) {
            GuiScreen parent = muiScreen.getScreen()
                .getContext()
                .getParentScreen();
            return parent instanceof GuiMainMenu || parent instanceof GuiMultiplayer;
        }
        return false;
    }

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

    static final class ServerAddRequest {

        final String name;
        final String address;

        ServerAddRequest(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }
}
