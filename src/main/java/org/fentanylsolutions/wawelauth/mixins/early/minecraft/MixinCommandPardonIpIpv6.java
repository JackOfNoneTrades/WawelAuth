package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.SyntaxErrorException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.command.server.CommandPardonIp;
import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = CommandPardonIp.class, priority = 999)
public abstract class MixinCommandPardonIpIpv6 extends CommandBase {

    /**
     * @author WawelAuth
     * @reason Remove strict IPv4-only validation in vanilla /pardon-ip.
     */
    @Overwrite
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && args[0].length() > 1) {
            if (NetworkAddressUtil.looksLikeIp(args[0])) {
                MinecraftServer.getServer()
                    .getConfigurationManager()
                    .getBannedIPs()
                    .func_152684_c(args[0]);
                func_152373_a(sender, this, "commands.unbanip.success", args[0]);
            } else {
                throw new SyntaxErrorException("commands.unbanip.invalid");
            }
        } else {
            throw new WrongUsageException("commands.unbanip.usage");
        }
    }
}
