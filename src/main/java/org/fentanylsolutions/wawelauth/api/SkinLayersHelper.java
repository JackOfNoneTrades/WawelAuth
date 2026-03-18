package org.fentanylsolutions.wawelauth.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayersConfig;

public class SkinLayersHelper {

    /*
     * Vanilla 1.7.10 : [1] - cape
     * Vanilla 1.8+ : [0-7], 8th bit unused
     * WawelAuth : [1-8], 0th bit unused
     * Keep 1.7.10 cape's ID same
     */
    public enum EnumPlayerModelParts {

        CAPE(1, "wawelauth.gui.skincustomization.cape", () -> !SkinLayersConfig.enableCape,
            v -> SkinLayersConfig.enableCape = !v),
        JACKET(2, "wawelauth.gui.skincustomization.jacket", () -> !SkinLayersConfig.enableJacket,
            v -> SkinLayersConfig.enableJacket = !v),
        LEFT_SLEEVE(3, "wawelauth.gui.skincustomization.left_sleeve", () -> !SkinLayersConfig.enableLeftSleeve,
            v -> SkinLayersConfig.enableLeftSleeve = !v),
        RIGHT_SLEEVE(4, "wawelauth.gui.skincustomization.right_sleeve", () -> !SkinLayersConfig.enableRightSleeve,
            v -> SkinLayersConfig.enableRightSleeve = !v),
        LEFT_PANTS(5, "wawelauth.gui.skincustomization.left_pants", () -> !SkinLayersConfig.enableLeftPants,
            v -> SkinLayersConfig.enableLeftPants = !v),
        RIGHT_PANTS(6, "wawelauth.gui.skincustomization.right_pants", () -> !SkinLayersConfig.enableRightPants,
            v -> SkinLayersConfig.enableRightPants = !v),
        HAT(7, "wawelauth.gui.skincustomization.hat", () -> !SkinLayersConfig.enableHat,
            v -> SkinLayersConfig.enableHat = !v);

        private final int partId;
        private final byte partMask;
        private final String partName;
        private final Supplier<Boolean> partGetter;
        private final Consumer<Boolean> partSetter;

        EnumPlayerModelParts(int partId, String partName, Supplier<Boolean> partGetter, Consumer<Boolean> partSetter) {
            this.partId = partId;
            this.partMask = (byte) (1 << partId);
            this.partName = partName;
            this.partGetter = partGetter;
            this.partSetter = partSetter;
        }

        public int getPartId() {
            return this.partId;
        }

        public byte getPartMask() {
            return this.partMask;
        }

        public String getPartName() {
            return this.partName;
        }

        public boolean getPartHidden() {
            return this.partGetter.get();
        }

        public void setPartHidden(boolean value) {
            this.partSetter.accept(value);
        }

        public static EnumPlayerModelParts fromId(int id) {
            for (EnumPlayerModelParts part : values()) {
                if (part.partId == id) return part;
            }
            return null;
        }
    }

    public static boolean isSkinLayerHidden(EntityPlayer player, EnumPlayerModelParts part) {
        return (player.getDataWatcher()
            .getWatchableObjectByte(16) & part.getPartMask()) != 0;
    }

    public static void setSkinLayerHidden(EntityPlayer player, EnumPlayerModelParts part, boolean hidden) {
        byte mask = player.getDataWatcher()
            .getWatchableObjectByte(16);
        if (hidden) {
            player.getDataWatcher()
                .updateObject(16, (byte) (mask | part.getPartMask()));
        } else {
            player.getDataWatcher()
                .updateObject(16, (byte) (mask & ~part.getPartMask()));
        }
    }

}
