package org.fentanylsolutions.wawelauth.client.gui;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;

public final class StatusColors {

    private StatusColors() {}

    public static final int GREEN = 0xFF00A651;
    public static final int YELLOW = 0xFFE6A700;
    public static final int RED = 0xFFE53935;
    public static final int GRAY = 0xFF555555;

    public static int getColor(AccountStatus status) {
        if (status == null) return GRAY;
        switch (status) {
            case VALID:
            case REFRESHED:
                return GREEN;
            case UNVERIFIED:
                return YELLOW;
            case UNAUTHED:
                return GRAY;
            case EXPIRED:
                return RED;
            default:
                return GRAY;
        }
    }

    public static String getLabel(AccountStatus status) {
        if (status == null) return GuiText.tr("wawelauth.gui.status.unknown");
        switch (status) {
            case VALID:
                return GuiText.tr("wawelauth.gui.status.valid");
            case REFRESHED:
                return GuiText.tr("wawelauth.gui.status.refreshed");
            case UNVERIFIED:
                return GuiText.tr("wawelauth.gui.status.unverified");
            case UNAUTHED:
                return GuiText.tr("wawelauth.gui.status.unauthed");
            case EXPIRED:
                return GuiText.tr("wawelauth.gui.status.expired");
            default:
                return GuiText.tr("wawelauth.gui.status.unknown");
        }
    }
}
