package org.fentanylsolutions.wawelauth.api;

import net.minecraftforge.client.MinecraftForgeClient;

/**
 * todo
 */
public enum RenderPassLayer {

    MAIN,
    TRANSLUCENT,
    GUI,;

    // do we really need to explain this

    /**
     *
     */
    public static RenderPassLayer getCurrent() {
        int pass = MinecraftForgeClient.getRenderPass();
        return switch (pass) {
            case 0 -> MAIN;
            case 1 -> TRANSLUCENT;
            default -> GUI;
        };
    }

    /**
     *
     */
    public boolean shouldRender(boolean behindTranslucent) {
        return switch (this) {
            case GUI -> true;
            case MAIN -> !behindTranslucent;
            case TRANSLUCENT -> behindTranslucent;
        };
    }
}
