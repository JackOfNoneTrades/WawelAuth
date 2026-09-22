package org.fentanylsolutions.wawelauth.client.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class EtFuturumCompat {

    private static Item elytra;

    public static void init() {
        if (Loader.isModLoaded("etfuturum")) {
            elytra = GameRegistry.findItem("etfuturum", "elytra");
        }
    }

    public static boolean isPreviewElytraAvailable() {
        return getElytraItem() != null;
    }

    public static void applyPreviewElytra(EntityLivingBase entity, boolean enabled) {
        if (entity == null) return;

        Item elytraItem = getElytraItem();
        ItemStack equipped = entity.getEquipmentInSlot(3);

        if (!enabled || elytraItem == null) {
            if (isPreviewElytra(equipped, elytraItem)) entity.setCurrentItemOrArmor(3, null);
            return;
        }

        if (!isPreviewElytra(equipped, elytraItem)) {
            entity.setCurrentItemOrArmor(3, new ItemStack(elytraItem));
        }
    }

    private static Item getElytraItem() {
        return elytra;
    }

    private static boolean isPreviewElytra(ItemStack stack, Item elytraItem) {
        return stack != null && elytraItem != null && stack.getItem() == elytraItem;
    }
}
