package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = EntityPlayer.class, priority = 999)
public abstract class MixinEntityPlayer extends EntityLivingBase {

    public MixinEntityPlayer(World p_i1594_1_) {
        super(p_i1594_1_);
    }

    /**
     * @author WawelAuth
     * @reason Redirect to unified system
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    protected boolean getHideCape(int p_82241_1_) {
        return SkinLayersHelper.isSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.CAPE);
    }

    /**
     * @author WawelAuth
     * @reason Redirect to unified system
     */
    @Overwrite
    protected void setHideCape(int p_82239_1_, boolean p_82239_2_) {
        SkinLayersHelper.setSkinLayerHidden(((EntityPlayer) (Object) this), SkinLayersHelper.EnumPlayerModelParts.CAPE, p_82239_2_);
    }

}
