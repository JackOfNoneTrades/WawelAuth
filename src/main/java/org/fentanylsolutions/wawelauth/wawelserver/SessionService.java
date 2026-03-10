package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.UUID;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.data.PendingSession;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelToken;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.SessionDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TokenDAO;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;
import org.fentanylsolutions.wawelauth.wawelnet.RequestContext;

import com.google.gson.JsonObject;

/**
 * Implements the join/hasJoined session handshake endpoints.
 */
public class SessionService {

    private static final String WAWELAUTH_CLIENT_MARKER = "wawelauth_client";

    private final TokenDAO tokenDAO;
    private final ProfileDAO profileDAO;
    private final SessionDAO sessionDAO;
    private final ProfileService profileService;
    private final FallbackProxyService fallbackProxyService;

    public SessionService(TokenDAO tokenDAO, ProfileDAO profileDAO, SessionDAO sessionDAO,
        ProfileService profileService, FallbackProxyService fallbackProxyService) {
        this.tokenDAO = tokenDAO;
        this.profileDAO = profileDAO;
        this.sessionDAO = sessionDAO;
        this.profileService = profileService;
        this.fallbackProxyService = fallbackProxyService;
    }

    /**
     * POST /sessionserver/session/minecraft/join
     *
     * Client calls this when connecting to a server. Creates a pending session
     * that the game server will verify via hasJoined.
     */
    public Object join(RequestContext ctx) {
        String accessToken = ctx.requireJsonString("accessToken");
        String serverId = ctx.requireJsonString("serverId");
        String selectedProfileStr = ctx.requireJsonString("selectedProfile");

        UUID profileUuid;
        try {
            profileUuid = UuidUtil.fromUnsigned(selectedProfileStr);
        } catch (IllegalArgumentException e) {
            throw NetException.forbidden("Invalid profile ID.");
        }

        // Validate the token
        WawelToken token = tokenDAO.findByAccessToken(accessToken);
        if (token == null || !token.isUsable()) {
            throw NetException.forbidden("Invalid token.");
        }

        // Token must be bound to the selected profile
        if (!profileUuid.equals(token.getProfileUuid())) {
            throw NetException.forbidden("Token is not bound to the selected profile.");
        }

        // Verify the profile exists
        WawelProfile profile = profileDAO.findByUuid(profileUuid);
        if (profile == null) {
            throw NetException.forbidden("Profile not found.");
        }

        // Verify profile ownership
        if (!profile.getOwnerUuid()
            .equals(token.getUserUuid())) {
            throw NetException.forbidden("Profile does not belong to token owner.");
        }

        // Update token last used
        token.setLastUsedAt(System.currentTimeMillis());
        tokenDAO.update(token);

        // Create pending session
        PendingSession session = new PendingSession(
            serverId,
            profileUuid,
            profile.getName(),
            accessToken,
            ctx.getClientIp());

        sessionDAO.create(session);

        return null; // 204
    }

    /**
     * GET /sessionserver/session/minecraft/hasJoined
     *
     * Game server calls this to verify a client's session.
     * Returns the full signed profile on success, or 204 on failure
     * (silent fail per spec: game server interprets non-200 as auth failure).
     */
    public Object hasJoined(RequestContext ctx) {
        String username = ctx.getQueryParam("username");
        String serverId = ctx.getQueryParam("serverId");
        String ip = ctx.getQueryParam("ip");

        if (username == null || serverId == null) {
            return null; // 204: silent fail
        }

        long timeoutMs = Config.server()
            .getTokens()
            .getSessionTimeoutMs();

        PendingSession session = sessionDAO.consume(serverId, username, ip, timeoutMs);
        if (session == null) {
            JsonObject fallback = fallbackProxyService.resolveHasJoined(username, serverId, ip);
            if (fallback != null) {
                return fallback;
            }
            return null; // 204: silent fail
        }

        // Build full signed profile response (hasJoined is always signed)
        WawelProfile profile = profileDAO.findByUuid(session.getProfileUuid());
        if (profile == null) {
            return null; // 204: profile was deleted between join and hasJoined
        }

        return profileService.buildFullProfile(profile, true);
    }

    /**
     * GET /sessionserver/session/minecraft/profile/{uuid}
     *
     * Profile query endpoint. Returns the full profile with textures.
     * Signed only when the "unsigned" query parameter is explicitly "false".
     */
    public Object profileByUuid(RequestContext ctx) {
        String uuidStr = ctx.getPathParam("uuid");
        String unsignedParam = ctx.getQueryParam("unsigned");

        UUID uuid;
        try {
            uuid = UuidUtil.fromUnsigned(uuidStr);
        } catch (IllegalArgumentException e) {
            throw NetException.illegalArgument("Invalid UUID format.");
        }

        WawelProfile profile = profileDAO.findByUuid(uuid);
        if (profile == null) {
            JsonObject fallback = fallbackProxyService.resolveProfileByUuid(uuidStr, unsignedParam);
            if (fallback != null) {
                boolean signed = "false".equalsIgnoreCase(unsignedParam);
                if (signed && isWawelAuthClientProfileRequest(ctx)) {
                    WawelAuth.debug("Re-signing fallback profile " + uuidStr + " for WawelAuth client request");
                    return profileService.resignTexturesProperty(fallback);
                }
                return fallback;
            }
            throw NetException.notFound("Profile not found.");
        }

        // Spec: "When unsigned=false, return signature for textures property."
        // Default (absent param) is unsigned: only sign when unsigned=false explicitly.
        boolean signed = "false".equalsIgnoreCase(unsignedParam);

        return profileService.buildFullProfile(profile, signed);
    }

    private static boolean isWawelAuthClientProfileRequest(RequestContext ctx) {
        if (ctx == null) {
            return false;
        }
        String marker = ctx.getQueryParam(WAWELAUTH_CLIENT_MARKER);
        return "1".equals(marker) || "true".equalsIgnoreCase(marker);
    }
}
