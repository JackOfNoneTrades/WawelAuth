package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Map;

import net.minecraft.server.management.UserList;
import net.minecraft.server.management.UserListEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(UserList.class)
public interface AccessorUserList {

    @Invoker("func_152688_e")
    Map<String, UserListEntry> wawelauth$getEntries();
}
