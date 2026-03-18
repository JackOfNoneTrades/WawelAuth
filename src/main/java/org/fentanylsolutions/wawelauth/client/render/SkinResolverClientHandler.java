package org.fentanylsolutions.wawelauth.client.render;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.common.ISkinLayerExtender;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Drives WawelTextureResolver lifecycle: tick sweep, invalidate on join, clear on disconnect.
 * <p>
 * Disables skin overlay rendering when armor is equipped.
 */
@SideOnly(Side.CLIENT)
public final class SkinResolverClientHandler {

    private static final SkinResolverClientHandler INSTANCE = new SkinResolverClientHandler();
    private static volatile boolean registered;

    private SkinResolverClientHandler() {}

    public static synchronized void register() {
        if (registered) return;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WawelClient client = WawelClient.instance();
        if (client != null) {
            client.getTextureResolver()
                .tick();
        }
    }

    @SubscribeEvent
    public void onOtherPlayerJoin(EntityJoinWorldEvent event) {
        if (!(event.entity instanceof EntityOtherPlayerMP playerMP)) {
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        UUID playerID = playerMP.getUniqueID();
        if (playerID == null) return;

        client.getTextureResolver()
            .invalidate(playerID);
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                SkinLayers3DSetup.updateSkullCache(playerID, null);
                SkinLayers3DSetup.updateState(playerID, null);
            });
    }

    @SubscribeEvent
    public void onPlayerLeaveFMLEvent(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        client.getTextureResolver()
            .invalidateAll();
        client.getConnectionProviderCache()
            .clear();
        LocalTextureLoader.clearImageCache();
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                SkinLayers3DSetup.clearSkullCache();
                SkinLayers3DSetup.clearState();
            });
    }

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.entityPlayer;
        RenderPlayer renderer = event.renderer;
        IModelBipedModernExt ext = (IModelBipedModernExt) renderer.modelBipedMain;

        if (((ISkinLayerExtender) player).wawelAuth$getHideHat()) {
            renderer.modelBipedMain.bipedHeadwear.showModel = false;
        }
        if (((ISkinLayerExtender) player).wawelAuth$getHideJacket()) {
            ext.wawelAuth$getBodyWear().showModel = false;
        }
        if (((ISkinLayerExtender) player).wawelAuth$getLeftSleeve()) {
            ext.wawelAuth$getLeftArmWear().showModel = false;
        }
        if (((ISkinLayerExtender) player).wawelAuth$getRightSleeve()) {
            ext.wawelAuth$getRightArmWear().showModel = false;
        }
        if (((ISkinLayerExtender) player).wawelAuth$getLeftPants()) {
            ext.wawelAuth$getLeftLegWear().showModel = false;
        }
        if (((ISkinLayerExtender) player).wawelAuth$getRightPants()) {
            ext.wawelAuth$getRightLegWear().showModel = false;
        }

        if (SkinLayers3DConfig.hideOverlayArmor) {
            ItemStack[] armor = player.inventory.armorInventory;

            ItemStack helmet = armor[3];
            ItemStack chest = armor[2];
            ItemStack legs = armor[1];
            ItemStack boots = armor[0];

            if (helmet != null && helmet.getItem() instanceof ItemArmor) {
                renderer.modelBipedMain.bipedHeadwear.showModel = false;
            }
            if (chest != null && chest.getItem() instanceof ItemArmor) {
                ext.wawelAuth$getBodyWear().showModel = false;
                ext.wawelAuth$getLeftArmWear().showModel = false;
                ext.wawelAuth$getRightArmWear().showModel = false;
            }
            if ((legs != null && legs.getItem() instanceof ItemArmor)
                || (boots != null && boots.getItem() instanceof ItemArmor)) {
                ext.wawelAuth$getLeftLegWear().showModel = false;
                ext.wawelAuth$getRightLegWear().showModel = false;
            }
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

}
