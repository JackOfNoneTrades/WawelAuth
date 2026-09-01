package org.fentanylsolutions.wawelauth.api;

import net.minecraft.entity.player.EntityPlayer;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayersConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SkinLayersHelper {

    /**
     * Uses 1.8+ ids to avoid signed byte overflow
     */
    public enum EnumPlayerModelParts {

        CAPE(0, "wawelauth.gui.skincustomization.cape", () -> !SkinLayersConfig.enableCape,
            v -> SkinLayersConfig.enableCape = !v, false),
        JACKET(1, "wawelauth.gui.skincustomization.jacket", () -> !SkinLayersConfig.enableJacket,
            v -> SkinLayersConfig.enableJacket = !v, true),
        LEFT_SLEEVE(2, "wawelauth.gui.skincustomization.left_sleeve", () -> !SkinLayersConfig.enableLeftSleeve,
            v -> SkinLayersConfig.enableLeftSleeve = !v, true),
        RIGHT_SLEEVE(3, "wawelauth.gui.skincustomization.right_sleeve", () -> !SkinLayersConfig.enableRightSleeve,
            v -> SkinLayersConfig.enableRightSleeve = !v, true),
        LEFT_PANTS(4, "wawelauth.gui.skincustomization.left_pants", () -> !SkinLayersConfig.enableLeftPants,
            v -> SkinLayersConfig.enableLeftPants = !v, true),
        RIGHT_PANTS(5, "wawelauth.gui.skincustomization.right_pants", () -> !SkinLayersConfig.enableRightPants,
            v -> SkinLayersConfig.enableRightPants = !v, true),
        HAT(6, "wawelauth.gui.skincustomization.hat", () -> !SkinLayersConfig.enableHat,
            v -> SkinLayersConfig.enableHat = !v, false);

        private final int partId;
        private final byte partMask;
        private final String partName;
        private final Supplier<Boolean> partGetter;
        private final Consumer<Boolean> partSetter;
        private final boolean partIsModern;

        EnumPlayerModelParts(int partId, String partName, Supplier<Boolean> partGetter, Consumer<Boolean> partSetter, boolean partIsModern) {
            this.partId = partId;
            this.partMask = (byte) (1 << partId);
            this.partName = partName;
            this.partGetter = partGetter;
            this.partSetter = partSetter;
            this.partIsModern = partIsModern;
        }

        public int id() {
            return this.partId;
        }

        public byte mask() {
            return this.partMask;
        }

        public String partName() {
            return this.partName;
        }

        public boolean hidden() {
            return this.partGetter.get();
        }

        public void setHidden(boolean value) {
            this.partSetter.accept(value);
        }

        public boolean isModern() {
            return this.partIsModern;
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
            .getWatchableObjectByte(16) & part.mask()) != 0;
    }

    public static void setSkinLayerHidden(EntityPlayer player, EnumPlayerModelParts part, boolean hidden) {
        byte mask = player.getDataWatcher()
            .getWatchableObjectByte(16);
        if (hidden) {
            player.getDataWatcher()
                .updateObject(16, (byte) (mask | part.mask()));
        } else {
            player.getDataWatcher()
                .updateObject(16, (byte) (mask & (~part.mask() & 127)));
        }
    }

}
