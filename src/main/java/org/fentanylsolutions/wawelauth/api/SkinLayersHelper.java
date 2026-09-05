package org.fentanylsolutions.wawelauth.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.client.fakeworld.PreviewEntityRenderContext;
import org.fentanylsolutions.wawelauth.config.SkinLayersConfig;

/**
 * Various utilities for managing player overlays
 */
public class SkinLayersHelper {

    public enum EnumPlayerModelParts {

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

        private final String partName;
        private final Supplier<PartState> stateSupplier;
        private final Consumer<PartState> stateConsumer;

        public static final EnumPlayerModelParts[] VALUES = values();
        public static final int COUNT = VALUES.length;

        EnumPlayerModelParts(String partName, Supplier<PartState> stateS, Consumer<PartState> stateC) {
            this.partName = partName;
            this.stateSupplier = stateS;
            this.stateConsumer = stateC;
        }

        /**
         * Translation key for GUI use
         */
        public String partName() {
            return this.partName;
        }

        /**
         * Gets current client state of overlay | Packet sending
         */
        public Supplier<PartState> stateS() {
            return this.stateSupplier;
        }

        /**
         * Sets current client state of overlay | GUI
         */
        public Consumer<PartState> stateC() {
            return this.stateConsumer;
        }

        public static EnumPlayerModelParts fromOrdinal(int ordinal) {
            for (EnumPlayerModelParts part : VALUES) {
                if (part.ordinal() == ordinal) return part;
            }
            return null;
        }
    }

    public enum PartState {

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

        public static final PartState[] VALUES = values();

        public PartState next() {
            return VALUES[(this.ordinal() + 1) % VALUES.length];
        }
    }

    /**
     * Gets overlay state for a specific player | Clientside
     */
    public static PartState getSkinLayerState(EntityPlayer player, EnumPlayerModelParts part) {
        if (PreviewEntityRenderContext.isRenderingInGui) return part.stateS()
            .get();

        short mask = player.getDataWatcher()
            .getWatchableObjectShort(16);
        int tmask = mask & 0xFFFF;
        int shift = part.ordinal() * 2;
        int bits = (tmask >> shift) & 3;
        return switch (bits) {
            case 1 -> PartState.FLAT;
            case 3 -> PartState.VOLUMETRIC;
            default -> PartState.DISABLED;
        };
    }

    /**
     * Sets overlay state for a specific player | Serverside
     */
    public static void setSkinLayerState(EntityPlayer player, EnumPlayerModelParts part, PartState state) {
        short mask = player.getDataWatcher()
            .getWatchableObjectShort(16);
        int tmask = mask & 0xFFFF;
        int shift = part.ordinal() * 2;
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
