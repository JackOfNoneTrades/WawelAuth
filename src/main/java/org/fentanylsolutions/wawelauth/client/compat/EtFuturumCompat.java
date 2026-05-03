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

    private static final String MODID = "etfuturum";
    private static final int CHEST_SLOT = 3;

    private static boolean elytraLookupAttempted;
    private static Item cachedElytraItem;

    private EtFuturumCompat() {}

    public static boolean isPreviewElytraAvailable() {
        return getElytraItem() != null;
    }

    public static void applyPreviewElytra(EntityLivingBase entity, boolean enabled) {
        if (entity == null) {
            return;
        }

        Item elytraItem = getElytraItem();
        ItemStack equipped = entity.getEquipmentInSlot(CHEST_SLOT);

        if (!enabled || elytraItem == null) {
            if (isPreviewElytra(equipped, elytraItem)) {
                entity.setCurrentItemOrArmor(CHEST_SLOT, null);
            }
            return;
        }

        if (!isPreviewElytra(equipped, elytraItem)) {
            entity.setCurrentItemOrArmor(CHEST_SLOT, new ItemStack(elytraItem));
        }
    }

    private static Item getElytraItem() {
        if (!Loader.isModLoaded(MODID)) {
            return null;
        }
        if (!elytraLookupAttempted) {
            elytraLookupAttempted = true;
            cachedElytraItem = GameRegistry.findItem(MODID, "elytra");
        }
        return cachedElytraItem;
    }

    private static boolean isPreviewElytra(ItemStack stack, Item elytraItem) {
        return stack != null && elytraItem != null && stack.getItem() == elytraItem;
    }
}
