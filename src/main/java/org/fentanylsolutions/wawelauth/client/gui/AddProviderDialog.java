package org.fentanylsolutions.wawelauth.client.gui;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ProviderRegistry;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class AddProviderDialog {

    private final Consumer<ClientProvider> onResult;
    private final IPanelHandler discoverHandler;
    private final IPanelHandler confirmHandler;
    private ClientProvider pendingProvider;

    private AddProviderDialog(ModularPanel parentPanel, Consumer<ClientProvider> onResult) {
        this.onResult = onResult;
        this.discoverHandler = IPanelHandler.simple(parentPanel, (parent, player) -> buildDiscoverDialog(), true);
        this.confirmHandler = IPanelHandler.simple(parentPanel, (parent, player) -> buildConfirmDialog(), true);
    }

    public static AddProviderDialog attach(ModularPanel parentPanel, Consumer<ClientProvider> onResult) {
        return new AddProviderDialog(parentPanel, onResult);
    }

    /**
     * Two-phase provider addition:
     * Phase 1: Discover: resolve ALI, fetch metadata + fingerprint (no persist).
     * Phase 2: Confirm: show fingerprint, persist only on explicit "Trust".
     */
    public void open() {
        this.pendingProvider = null;
        this.discoverHandler.deleteCachedPanel();
        this.discoverHandler.openPanel();
    }

    private Dialog<ClientProvider> buildDiscoverDialog() {
        Dialog<ClientProvider> dialog = new Dialog<>("wawelauth_add_provider", this.onResult);
        dialog.setCloseOnOutOfBoundsClick(false);

        TabTextFieldWidget nameField = new TabTextFieldWidget();
        nameField.hintText(GuiText.tr("wawelauth.gui.add_provider.hint.display_name"));
        TabTextFieldWidget urlField = new TabTextFieldWidget();
        urlField.hintText(GuiText.tr("wawelauth.gui.add_provider.hint.url"));

        String[] statusText = { "" };
        boolean[] busy = { false };

        ButtonWidget<?> discoverBtn = new ButtonWidget<>();
        ButtonWidget<?> cancelBtn = new ButtonWidget<>();

        cancelBtn.size(60, 18)
            .onMousePressed(mouseButton -> {
                dialog.closeWith(null);
                return true;
            });
        GuiText.fitButtonLabel(cancelBtn, 60, "wawelauth.gui.common.cancel");

        Runnable doDiscover = () -> {
            if (busy[0]) return;

            String name = nameField.getText()
                .trim();
            String url = urlField.getText()
                .trim();

            if (name.isEmpty()) {
                statusText[0] = GuiText.tr("wawelauth.gui.add_provider.error.name_required");
                return;
            }
            if (url.isEmpty()) {
                statusText[0] = GuiText.tr("wawelauth.gui.add_provider.error.url_required");
                return;
            }

            busy[0] = true;
            statusText[0] = GuiText.tr("wawelauth.gui.add_provider.status.discovering");

            ProviderRegistry registry = WawelClient.instance()
                .getProviderRegistry();
            CompletableFuture.supplyAsync(() -> {
                try {
                    return registry.discoverCustomProvider(name, url);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
                .whenComplete((provider, err) -> {
                    Minecraft.getMinecraft()
                        .func_152344_a(() -> { // Minecraft.addScheduledTask
                            busy[0] = false;
                            if (err != null) {
                                Throwable cause = err.getCause() != null ? err.getCause() : err;
                                statusText[0] = cause.getMessage();
                                WawelAuth.debug("Provider discovery failed: " + cause.getMessage());
                            } else {
                                pendingProvider = provider;
                                dialog.closeIfOpen();
                                confirmHandler.deleteCachedPanel();
                                confirmHandler.openPanel();
                            }
                        });
                });
        };

        nameField.onEnterPressed(doDiscover);
        urlField.onEnterPressed(doDiscover);

        discoverBtn.size(70, 18)
            .onMousePressed(mouseButton -> {
                doDiscover.run();
                return true;
            });
        GuiText.fitButtonLabel(discoverBtn, 70, "wawelauth.gui.add_provider.discover");

        dialog.size(240, 155)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.add_provider.title")).widthRel(1.0f)
                            .height(12))
                    .child(
                        nameField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(32)
                            .margin(0, 3))
                    .child(
                        urlField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(256)
                            .margin(0, 3))
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFFF55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(cancelBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(discoverBtn)));

        return dialog;
    }

    private Dialog<ClientProvider> buildConfirmDialog() {
        ClientProvider provider = this.pendingProvider;
        Dialog<ClientProvider> dialog = new Dialog<>("wawelauth_confirm_provider", this.onResult);
        dialog.setCloseOnOutOfBoundsClick(false);

        if (provider == null) {
            dialog.size(220, 80)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.add_provider.no_pending")).widthRel(1.0f)
                                .height(12))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
                                .margin(0, 4)
                                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .child(
                                    GuiText
                                        .fitButtonLabel(
                                            new ButtonWidget<>().size(60, 18),
                                            60,
                                            "wawelauth.gui.common.close")
                                        .onMousePressed(mouseButton -> {
                                            dialog.closeWith(null);
                                            return true;
                                        }))));
            return dialog;
        }

        String fingerprint = provider.getPublicKeyFingerprint();
        String displayFp = fingerprint != null ? fingerprint : GuiText.tr("wawelauth.gui.add_provider.no_public_key");

        dialog.size(280, 130)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.add_provider.confirm_title", provider.getName()))
                            .widthRel(1.0f)
                            .height(12))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.common.api_root", provider.getApiRoot()))
                            .color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10)
                            .margin(0, 2))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.common.fingerprint")).widthRel(1.0f)
                            .height(10)
                            .margin(0, 2))
                    .child(
                        new TextWidget<>(IKey.str(displayFp)).color(0xFF55FFFF)
                            .scale(0.7f)
                            .widthRel(1.0f)
                            .height(10)
                            .margin(0, 2))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 4)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(
                                GuiText
                                    .fitButtonLabel(
                                        new ButtonWidget<>().size(60, 18),
                                        60,
                                        "wawelauth.gui.add_provider.reject")
                                    .onMousePressed(mouseButton -> {
                                        pendingProvider = null;
                                        dialog.closeWith(null);
                                        return true;
                                    }))
                            .child(new Widget<>().size(6, 18))
                            .child(
                                GuiText
                                    .fitButtonLabel(
                                        new ButtonWidget<>().size(60, 18),
                                        60,
                                        "wawelauth.gui.add_provider.trust")
                                    .onMousePressed(mouseButton -> {
                                        try {
                                            WawelClient.instance()
                                                .getProviderRegistry()
                                                .persistProvider(provider);
                                            pendingProvider = null;
                                            dialog.closeWith(provider);
                                        } catch (Exception e) {
                                            WawelAuth.LOG.warn("Failed to persist provider: {}", e.getMessage());
                                            pendingProvider = null;
                                            dialog.closeWith(null);
                                        }
                                        return true;
                                    }))));
        return dialog;
    }
}
