package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.common.ISkinLayerExtender;
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
     * @reason Redirect to unified system
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    protected boolean getHideCape(int p_82241_1_) {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.CAPE);
    }

    /**
     * @author WawelAuth
     * @reason Redirect to unified system
     */
    @Overwrite
    protected void setHideCape(int p_82239_1_, boolean p_82239_2_) {
        SkinLayersHelper
            .setSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.CAPE, p_82239_2_);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wawelAuth$getHideJacket() {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.JACKET);
    }

    @Override
    public void wawelAuth$setHideJacket(boolean value) {
        SkinLayersHelper
            .setSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.JACKET, value);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wawelAuth$getHideLeftSleeve() {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.LEFT_SLEEVE);
    }

    @Override
    public void wawelAuth$setHideLeftSleeve(boolean value) {
        SkinLayersHelper.setSkinLayerHidden(
            ((EntityPlayer) (Object) this),
            SkinLayersHelper.EnumPlayerModelParts.LEFT_SLEEVE,
            value);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wawelAuth$getHideRightSleeve() {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.RIGHT_SLEEVE);
    }

    @Override
    public void wawelAuth$setHideRightSleeve(boolean value) {
        SkinLayersHelper.setSkinLayerHidden(
            ((EntityPlayer) (Object) this),
            SkinLayersHelper.EnumPlayerModelParts.RIGHT_SLEEVE,
            value);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wawelAuth$getHideLeftPants() {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.LEFT_PANTS);
    }

    @Override
    public void wawelAuth$setHideLeftPants(boolean value) {
        SkinLayersHelper.setSkinLayerHidden(
            ((EntityPlayer) (Object) this),
            SkinLayersHelper.EnumPlayerModelParts.LEFT_PANTS,
            value);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wawelAuth$getHideRightPants() {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.RIGHT_PANTS);
    }

    @Override
    public void wawelAuth$setHideRightPants(boolean value) {
        SkinLayersHelper.setSkinLayerHidden(
            ((EntityPlayer) (Object) this),
            SkinLayersHelper.EnumPlayerModelParts.RIGHT_PANTS,
            value);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wawelAuth$getHideHat() {
        return SkinLayersHelper
            .isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.HAT);
    }

    @Override
    public void wawelAuth$setHideHat(boolean value) {
        SkinLayersHelper
            .setSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.HAT, value);
    }

}
