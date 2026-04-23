package org.fentanylsolutions.wawelauth.client.fakeworld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.stats.StatFileWriter;

import com.cleanroommc.modularui.utils.fakeworld.DummyWorld;

public class DummyEntityClientPlayerMP extends EntityClientPlayerMP {

    public static final DummyEntityClientPlayerMP INSTANCE = new DummyEntityClientPlayerMP();

    public DummyEntityClientPlayerMP() {
        super(
            Minecraft.getMinecraft(),
            DummyWorld.INSTANCE,
            Minecraft.getMinecraft()
                .getSession(),
            null,
            null);
    }

    @Override
    public void onUpdate() {}

    @Override
    public void sendMotionUpdates() {}

    @Override
    public EntityItem dropOneItem(boolean p_71040_1_) {
        return null;
    }

    @Override
    public void sendChatMessage(String p_71165_1_) {}

    @Override
    public void swingItem() {}

    @Override
    public void respawnPlayer() {}

    @Override
    public void closeScreen() {}

    @Override
    public void sendPlayerAbilities() {}

    @Override
    protected void func_110318_g() {}

    @Override
    public void func_110322_i() {}

    @Override
    public StatFileWriter getStatFileWriter() {
        return null;
    }

}
