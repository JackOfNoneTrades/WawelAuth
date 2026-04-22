package org.fentanylsolutions.wawelauth.client.fakeworld;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetworkManager;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.utils.fakeworld.DummyChunkProvider;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

public class DummyWorldClient extends WorldClient {

    private static final WorldSettings DEFAULT_SETTINGS = new WorldSettings(
        1L,
        WorldSettings.GameType.SURVIVAL,
        true,
        false,
        WorldType.DEFAULT);
    public static final DummyWorldClient INSTANCE = new DummyWorldClient();

    private DummyWorldClient() {
        super(
            new NetHandlerPlayClient(Minecraft.getMinecraft(), null, new NetworkManager(true)),
            DEFAULT_SETTINGS,
            0,
            EnumDifficulty.PEACEFUL,
            new Profiler());
        this.provider.setDimension(Integer.MAX_VALUE);
        int providerDim = this.provider.dimensionId;
        this.provider.registerWorld(this);
        this.provider.setDimension(providerDim);
        this.chunkProvider = this.createChunkProvider();
        this.calculateInitialSkylight();
        // De-allocate lightUpdateBlockList, checkLightFor uses this
        ObfuscationReflectionHelper
            .setPrivateValue(World.class, this, null, ModularUI.isDevEnv ? "lightUpdateBlockList" : "field_72994_J");
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return new DummyChunkProvider(this);
    }

    @Override
    protected int func_152379_p() {
        return -1;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return 0;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return null;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int unknown) {
        return super.getLightBrightnessForSkyBlocks(x, y, z, unknown);
    }

    @Override
    public float getLightBrightness(int x, int y, int z) {
        return super.getLightBrightness(x, y, z);
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int face) {
        return 0;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return true;
    }

    @Override
    public Entity getEntityByID(int id) {
        return null;
    }

    @Override
    public boolean spawnEntityInWorld(Entity entity) {
        return false;
    }

    @Override
    public void spawnParticle(String type, double x, double y, double z, double r, double g, double b) {}

    @Override
    public boolean setBlock(int p_147449_1_, int p_147449_2_, int p_147449_3_, Block p_147449_4_) {
        return false;
    }

    @Override
    public boolean setBlock(int p_147465_1_, int p_147465_2_, int p_147465_3_, Block p_147465_4_, int p_147465_5_,
        int p_147465_6_) {
        return false;
    }

    @Override
    public boolean setBlockMetadataWithNotify(int p_72921_1_, int p_72921_2_, int p_72921_3_, int p_72921_4_,
        int p_72921_5_) {
        return false;
    }

    @Override
    public boolean setBlockToAir(int p_147468_1_, int p_147468_2_, int p_147468_3_) {
        return false;
    }

    @Override
    public void setTileEntity(int x, int y, int z, TileEntity te) {}

    @Override
    public void removeTileEntity(int x, int y, int z) {}

    @Override
    public void tick() {}

    @Override
    public void updateEntities() {}

    @Override
    public void playSound(double x, double y, double z, String sound, float volume, float pitch, boolean unknown) {}

    @Override
    public void playSoundAtEntity(Entity entity, String sound, float volume, float pitch) {}

    @Override
    public void playSoundEffect(double x, double y, double z, String sound, float volume, float pitch) {}

    @Override
    public void playAuxSFX(int x, int y, int z, int a, int b) {}

    @Override
    public void playAuxSFXAtEntity(EntityPlayer player, int x, int y, int z, int a, int b) {}

    @Override
    public void playSoundToNearExcept(EntityPlayer player, String sound, float volume, float pitch) {}

    @Override
    public boolean updateLightByType(EnumSkyBlock b, int x, int y, int z) {
        return true;
    }

    @Override
    public Chunk getChunkFromBlockCoords(int x, int z) {
        return null;
    }

    @Override
    public Chunk getChunkFromChunkCoords(int x, int z) {
        return null;
    }

    @Override
    public IChunkProvider getChunkProvider() {
        return null;
    }

    @Override
    protected boolean chunkExists(int p_72916_1_, int p_72916_2_) {
        return true;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side) {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        return _default;
    }

    @Override
    public void notifyBlockChange(int p_147444_1_, int p_147444_2_, int p_147444_3_, Block p_147444_4_) {}

    @Override
    public void notifyBlockOfNeighborChange(int p_147460_1_, int p_147460_2_, int p_147460_3_, Block p_147460_4_) {

    }

    @Override
    public void notifyBlocksOfNeighborChange(int p_147441_1_, int p_147441_2_, int p_147441_3_, Block p_147441_4_,
        int p_147441_5_) {

    }

    @Override
    public void notifyBlocksOfNeighborChange(int p_147459_1_, int p_147459_2_, int p_147459_3_, Block p_147459_4_) {

    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}

    @Override
    public void markBlockForUpdate(int x, int y, int z) {}

    @Override
    public long getWorldTime() {
        return 0;
    }

    @Override
    public long getTotalWorldTime() {
        return 0;
    }

}
