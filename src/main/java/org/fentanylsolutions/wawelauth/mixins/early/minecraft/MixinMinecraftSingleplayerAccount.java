package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.SingleplayerAccountPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftSingleplayerAccount {

    @Inject(method = "launchIntegratedServer", at = @At("HEAD"))
    private void wawelauth$activateSingleplayerAccount(String folderName, String worldName,
        WorldSettings worldSettingsIn, CallbackInfo ci) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        ClientAccount account = SingleplayerAccountPersistence.resolveSelectedAccount(client.getAccountManager());
        if (account == null) {
            client.getSessionBridge()
                .clearActiveAccount();
            return;
        }

        client.getSessionBridge()
            .activateAccount(account.getId());
        WawelAuth.debug(
            "Prepared singleplayer account '" + account.getProfileName()
                + "' before launching world '"
                + worldName
                + "'");
    }
}
