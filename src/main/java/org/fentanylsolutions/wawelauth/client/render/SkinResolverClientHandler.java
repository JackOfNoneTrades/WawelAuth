package org.fentanylsolutions.wawelauth.client.render;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.HAT;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.JACKET;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.LEFT_PANTS;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.LEFT_SLEEVE;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.RIGHT_PANTS;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.RIGHT_SLEEVE;

/**
 * Drives WawelTextureResolver lifecycle: tick sweep, invalidate on join, clear on disconnect.
 * <p>
 * Disables skin overlay rendering when armor is equipped.
 */
@SideOnly(Side.CLIENT)
public final class SkinResolverClientHandler {

    private static final SkinResolverClientHandler INSTANCE = new SkinResolverClientHandler();
    private static volatile boolean registered;

    private final Set<UUID> invalidatedPlayers = ConcurrentHashMap.newKeySet();

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

        // EntityJoinWorldEvent fires on every tracking-range re-entry, not just
        // logins; only the first sighting per world should force a refetch.
        if (!invalidatedPlayers.add(playerID)) return;

        client.getTextureResolver()
            .invalidate(playerID);
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                SkinLayers3DSetup.updateSkullCache(playerID, null);
                SkinLayers3DSetup.updateState(playerID, null);
            });
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            invalidatedPlayers.clear();
        }
    }

    @SubscribeEvent
    public void onPlayerLeaveFMLEvent(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        invalidatedPlayers.clear();
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
        ItemStack[] armor = player.inventory.armorInventory;

        if (SkinLayersHelper.isSkinLayerHidden(player, HAT)) ext.hidePart(HAT, true);
        if (SkinLayersHelper.isSkinLayerHidden(player, JACKET)) ext.hidePart(JACKET, true);
        if (SkinLayersHelper.isSkinLayerHidden(player, LEFT_SLEEVE)) ext.hidePart(LEFT_SLEEVE, true);
        if (SkinLayersHelper.isSkinLayerHidden(player, RIGHT_SLEEVE)) ext.hidePart(RIGHT_SLEEVE, true);
        if (SkinLayersHelper.isSkinLayerHidden(player, LEFT_PANTS)) ext.hidePart(LEFT_PANTS, true);
        if (SkinLayersHelper.isSkinLayerHidden(player, RIGHT_PANTS)) ext.hidePart(RIGHT_PANTS, true);

        if (SkinLayers3DConfig.hideOverlayArmor) {
            ItemStack head = armor[3];
            ItemStack chest = armor[2];
            ItemStack legs = armor[1];
            ItemStack boots = armor[0];

            if (head != null) ext.hidePart(HAT, true);
            if (chest != null) {
                ext.hidePart(JACKET, true);
                ext.hidePart(LEFT_SLEEVE, true);
                ext.hidePart(RIGHT_SLEEVE, true);
            }
            if (legs != null || boots != null) {
                ext.hidePart(LEFT_PANTS, true);
                ext.hidePart(RIGHT_PANTS, true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        RenderPlayer renderer = event.renderer;
        IModelBipedModernExt ext = (IModelBipedModernExt) renderer.modelBipedMain;

        ext.hidePart(HAT, false);
        ext.hidePart(JACKET, false);
        ext.hidePart(LEFT_SLEEVE, false);
        ext.hidePart(RIGHT_SLEEVE, false);
        ext.hidePart(LEFT_PANTS, false);
        ext.hidePart(RIGHT_PANTS, false);
    }

}
