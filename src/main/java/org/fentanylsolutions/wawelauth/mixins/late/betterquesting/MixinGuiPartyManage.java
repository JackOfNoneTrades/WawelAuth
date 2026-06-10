package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.client.gui2.party.GuiPartyManage;
import betterquesting.network.handlers.NetPartyAction;
import betterquesting.storage.NameCache;

@Mixin(value = GuiPartyManage.class, remap = false)
public abstract class MixinGuiPartyManage {

    @Shadow(remap = false)
    private IParty party;

    @Shadow(remap = false)
    private int partyID;

    @Inject(method = "onButtonPress", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$sendUuidForPartyMemberKick(PEventButton event, CallbackInfo ci) {
        if (event == null) {
            return;
        }

        IPanelButton button = event.getButton();
        if (button == null || button.getButtonID() != 3 || !(button instanceof PanelButtonStorage)) {
            return;
        }

        Object storedValue = ((PanelButtonStorage<?>) button).getStoredValue();
        UUID target = wawelauth$resolvePartyMember(storedValue == null ? null : storedValue.toString());
        if (target == null) {
            return;
        }

        NetPartyAction.requestKick(partyID, target.toString());
        ci.cancel();
    }

    private UUID wawelauth$resolvePartyMember(String storedValue) {
        UUID parsed = wawelauth$parseUuid(storedValue);
        if (parsed != null) {
            return parsed;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null
            && mc.thePlayer.getGameProfile() != null
            && storedValue != null
            && storedValue.equals(
                mc.thePlayer.getGameProfile()
                    .getName())) {
            return QuestingAPI.getQuestingUUID(mc.thePlayer);
        }

        if (party == null || storedValue == null) {
            return null;
        }

        for (UUID member : party.getMembers()) {
            String memberName = NameCache.INSTANCE.getName(member);
            if (storedValue.equals(memberName) || wawelauth$isTrimmedLabel(storedValue, memberName)) {
                return member;
            }
        }

        return null;
    }

    private static boolean wawelauth$isTrimmedLabel(String storedValue, String memberName) {
        if (storedValue == null || memberName == null || !storedValue.endsWith("...")) {
            return false;
        }

        String prefix = storedValue.substring(0, storedValue.length() - 3);
        return !prefix.isEmpty() && memberName.startsWith(prefix);
    }

    private static UUID wawelauth$parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}
