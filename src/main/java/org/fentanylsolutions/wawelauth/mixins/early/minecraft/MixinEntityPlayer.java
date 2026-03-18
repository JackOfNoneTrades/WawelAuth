package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.ISkinLayerExtender;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayersHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mixin(value = EntityPlayer.class, priority = 999)
public abstract class MixinEntityPlayer extends EntityLivingBase implements ISkinLayerExtender {

    public MixinEntityPlayer(World p_i1594_1_) {
        super(p_i1594_1_);
    }

    /**
     * @author WawelAuth
     * @reason todo
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    protected boolean getHideCape(int p_82241_1_) {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.CAPE);
    }

    /**
     * @author WawelAuth
     * @reason todo
     */
    @Overwrite
    protected void setHideCape(int p_82239_1_, boolean p_82239_2_) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.CAPE, p_82239_2_);
    }

    @SideOnly(Side.CLIENT)
    public boolean wawelAuth$getHideJacket() {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.JACKET);
    }

    @Override
    public void wawelAuth$setHideJacket(boolean value) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.JACKET, value);
    }

    @SideOnly(Side.CLIENT)
    public boolean wawelAuth$getLeftSleeve() {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.LEFT_SLEEVE);
    }

    @Override
    public void wawelAuth$setHideLeftSleeve(boolean value) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.LEFT_SLEEVE, value);
    }

    @SideOnly(Side.CLIENT)
    public boolean wawelAuth$getRightSleeve() {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.RIGHT_SLEEVE);
    }

    @Override
    public void wawelAuth$setRightSleeve(boolean value) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.RIGHT_SLEEVE, value);
    }

    @SideOnly(Side.CLIENT)
    public boolean wawelAuth$getLeftPants() {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.LEFT_PANTS);
    }

    @Override
    public void wawelAuth$setLeftPants(boolean value) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.LEFT_PANTS, value);
    }

    @SideOnly(Side.CLIENT)
    public boolean wawelAuth$getRightPants() {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.RIGHT_PANTS);
    }

    @Override
    public void wawelAuth$setRightPants(boolean value) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.RIGHT_PANTS, value);
    }

    @SideOnly(Side.CLIENT)
    public boolean wawelAuth$getHideHat() {
        return SkinLayersHelper.isSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.HAT);
    }

    @Override
    public void wawelAuth$setHideHat(boolean value) {
        SkinLayersHelper.setSkinLayerVisible(((EntityPlayer) (Object) this), SkinLayersHelper.HAT, value);
    }

}
