package org.fentanylsolutions.wawelauth.api.modernskinsupport;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.client.fakeworld.PreviewEntityRenderContext;
import org.fentanylsolutions.wawelauth.config.SkinLayersConfig;

/**
 * Various utilities for managing player overlays
 *
 * @author kotmatross
 */
public class SkinLayersHelper {

    public enum SkinLayer {

        CAPE("wawelauth.gui.skincustomization.cape", () -> (SkinLayersConfig.cape), v -> SkinLayersConfig.cape = v),
        JACKET("wawelauth.gui.skincustomization.jacket", () -> SkinLayersConfig.jacket,
            v -> SkinLayersConfig.jacket = v),
        LEFT_SLEEVE("wawelauth.gui.skincustomization.left_sleeve", () -> SkinLayersConfig.leftSleeve,
            v -> SkinLayersConfig.leftSleeve = v),
        RIGHT_SLEEVE("wawelauth.gui.skincustomization.right_sleeve", () -> SkinLayersConfig.rightSleeve,
            v -> SkinLayersConfig.rightSleeve = v),
        LEFT_PANTS("wawelauth.gui.skincustomization.left_pants", () -> SkinLayersConfig.leftPants,
            v -> SkinLayersConfig.leftPants = v),
        RIGHT_PANTS("wawelauth.gui.skincustomization.right_pants", () -> SkinLayersConfig.rightPants,
            v -> SkinLayersConfig.rightPants = v),
        HAT("wawelauth.gui.skincustomization.hat", () -> SkinLayersConfig.hat, v -> SkinLayersConfig.hat = v),

        ;

        private final String layerName;
        private final Supplier<LayerState> stateSupplier;
        private final Consumer<LayerState> stateConsumer;

        public static final SkinLayer[] VALUES = values();
        public static final int COUNT = VALUES.length;

        SkinLayer(String layerName, Supplier<LayerState> stateS, Consumer<LayerState> stateC) {
            this.layerName = layerName;
            this.stateSupplier = stateS;
            this.stateConsumer = stateC;
        }

        /**
         * Translation key for GUI use
         */
        public String layerName() {
            return this.layerName;
        }

        /**
         * Gets current client state of overlay | Packet sending
         */
        public Supplier<LayerState> stateGetter() {
            return this.stateSupplier;
        }

        /**
         * Sets current client state of overlay | GUI
         */
        public Consumer<LayerState> stateSetter() {
            return this.stateConsumer;
        }

        public static SkinLayer fromOrdinal(int ordinal) {
            for (SkinLayer layer : VALUES) {
                if (layer.ordinal() == ordinal) return layer;
            }
            return null;
        }
    }

    public enum LayerState {

        DISABLED,
        FLAT,
        VOLUMETRIC,

        ;

        public boolean isDisabled() {
            return this == DISABLED;
        }

        public boolean is2D() {
            return this == FLAT;
        }

        public boolean is3D() {
            return this == VOLUMETRIC;
        }

        public static final LayerState[] VALUES = values();

        public LayerState next() {
            return VALUES[(this.ordinal() + 1) % VALUES.length];
        }
    }

    /**
     * Gets overlay state for a specific player | Clientside
     */
    public static LayerState getSkinLayerState(EntityPlayer player, SkinLayer layer) {
        /// Max visibility since it's preview
        if (PreviewEntityRenderContext.isRenderingInGui) return LayerState.VOLUMETRIC;

        short mask = player.getDataWatcher()
            .getWatchableObjectShort(16);
        int tmask = mask & 0xFFFF;
        int shift = layer.ordinal() * 2;
        int bits = (tmask >> shift) & 3;
        return switch (bits) {
            case 1 -> LayerState.FLAT;
            case 3 -> LayerState.VOLUMETRIC;
            default -> LayerState.DISABLED;
        };
    }

    /**
     * Sets overlay state for a specific player | Serverside
     */
    public static void setSkinLayerState(EntityPlayer player, SkinLayer layer, LayerState state) {
        short mask = player.getDataWatcher()
            .getWatchableObjectShort(16);
        int tmask = mask & 0xFFFF;
        int shift = layer.ordinal() * 2;
        int bitValue = switch (state) {
            case DISABLED -> 0;
            case FLAT -> 1;
            case VOLUMETRIC -> 3;
        };
        tmask &= ~(3 << shift);
        tmask |= (bitValue << shift);
        player.getDataWatcher()
            .updateObject(16, (short) tmask);
    }

}
