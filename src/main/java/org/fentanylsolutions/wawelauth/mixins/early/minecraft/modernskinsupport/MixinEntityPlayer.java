package org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mixin(value = EntityPlayer.class, priority = 999)
public abstract class MixinEntityPlayer extends EntityLivingBase {

    /**
     * Support for translucent overlays
     */
    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    /**
     * 7 parts, each with 3 states : 12 bits are required
     */
    @WrapOperation(
        method = "entityInit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/DataWatcher;addObject(ILjava/lang/Object;)V"))
    private void changeDWType(DataWatcher instance, int p_75682_1_, Object p_75682_2_, Operation<Void> original) {
        if (p_75682_1_ == 16 && p_75682_2_ instanceof Byte) {
            original.call(instance, p_75682_1_, Short.valueOf((short) 0));
            return;
        }
        original.call(instance, p_75682_1_, p_75682_2_);
    }

    /**
     * @author WawelAuth
     * @reason Redirect to unified system
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    protected boolean getHideCape(int p_82241_1_) {
        return SkinLayersHelper.getSkinLayerState(((EntityPlayer) (Object) this), SkinLayersHelper.SkinLayer.CAPE)
            .isDisabled();
    }

    /**
     * @author WawelAuth
     * @reason Redirect to unified system
     */
    @Overwrite
    protected void setHideCape(int p_82239_1_, boolean p_82239_2_) {
        SkinLayersHelper.setSkinLayerState(
            ((EntityPlayer) (Object) this),
            SkinLayersHelper.SkinLayer.CAPE,
            p_82239_2_ ? SkinLayersHelper.LayerState.DISABLED : SkinLayersHelper.LayerState.FLAT);
    }

    public MixinEntityPlayer(World p_i1594_1_) {
        super(p_i1594_1_);
    }

}
