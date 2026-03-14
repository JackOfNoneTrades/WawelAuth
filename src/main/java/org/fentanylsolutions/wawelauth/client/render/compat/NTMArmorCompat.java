package org.fentanylsolutions.wawelauth.client.render.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.fentanylsolutions.wawelauth.client.render.IModelBipedModernExt;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NTMArmorCompat {

    private static final NTMArmorCompat INSTANCE = new NTMArmorCompat();

    public static void register() {
        if (!isAvailable()) return;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    private static boolean isAvailable() {
        return Loader.isModLoaded("hbm");
    }

    @SubscribeEvent
    public void preRenderEvent(RenderPlayerEvent.Pre event) {

        AbstractClientPlayer player = (AbstractClientPlayer) event.entityPlayer;
        RenderPlayer renderer = event.renderer;
        IModelBipedModernExt ext = (IModelBipedModernExt) renderer.modelBipedMain;

        ItemStack[] armor = player.inventory.armorInventory;

        ItemStack helmet = armor[3];
        ItemStack chest = armor[2];
        ItemStack legs = armor[1];
        ItemStack boots = armor[0];

        if (helmet != null && isFSBArmor(helmet.getItem())) {
            renderer.modelBipedMain.bipedHeadwear.showModel = false;
        }

        if (chest != null && isFSBArmor(chest.getItem())) {
            ext.wawelAuth$getBodyWear().showModel = false;
            ext.wawelAuth$getLeftArmWear().showModel = false;
            ext.wawelAuth$getRightArmWear().showModel = false;
        }

        boolean hasLowerArmor = (legs != null && isFSBArmor(legs.getItem()))
            || (boots != null && isFSBArmor(boots.getItem()));

        if (hasLowerArmor) {
            ext.wawelAuth$getLeftLegWear().showModel = false;
            ext.wawelAuth$getRightLegWear().showModel = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        RenderPlayer renderer = event.renderer;
        IModelBipedModernExt ext = (IModelBipedModernExt) renderer.modelBipedMain;

        renderer.modelBipedMain.bipedHeadwear.showModel = true;

        ext.wawelAuth$getBodyWear().showModel = true;
        ext.wawelAuth$getLeftArmWear().showModel = true;
        ext.wawelAuth$getRightArmWear().showModel = true;

        ext.wawelAuth$getLeftLegWear().showModel = true;
        ext.wawelAuth$getRightLegWear().showModel = true;
    }

    private static final MethodHandle IS_ARMOR_FSB;

    static {
        MethodHandle mh;
        try {
            Class<?> clazz = Class.forName("com.hbm.items.armor.ArmorFSB");
            MethodHandle base = MethodHandles.lookup()
                .findVirtual(Class.class, "isInstance", MethodType.methodType(boolean.class, Object.class));
            mh = base.bindTo(clazz)
                .asType(MethodType.methodType(boolean.class, Object.class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            mh = MethodHandles.constant(boolean.class, false);
        }
        IS_ARMOR_FSB = mh;
    }

    private static boolean isFSBArmor(Object item) {
        try {
            return (boolean) IS_ARMOR_FSB.invokeExact(item);
        } catch (Throwable t) {
            return false;
        }
    }

}
