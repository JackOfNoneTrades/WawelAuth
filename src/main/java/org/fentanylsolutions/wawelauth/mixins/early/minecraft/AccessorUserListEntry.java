package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.server.management.UserListEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(UserListEntry.class)
public interface AccessorUserListEntry {

    @Invoker("func_152640_f")
    Object wawelauth$getValue();
}
