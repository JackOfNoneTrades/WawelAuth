package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.wawelcore.config.RegistrationPolicy;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.PasswordHasher;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.fentanylsolutions.wawelauth.wawelcore.data.TokenState;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelToken;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelUser;
import org.fentanylsolutions.wawelauth.wawelcore.storage.InviteDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TokenDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.UserDAO;
import org.fentanylsolutions.wawelauth.wawelcore.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;
import org.fentanylsolutions.wawelauth.wawelnet.RequestContext;

/**
 * Implements the five /authserver/* Yggdrasil endpoints.
 */
public class AuthService {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ProfileDAO profileDAO;
    private final InviteDAO inviteDAO;
    private final ProfileService profileService;

    public AuthService(UserDAO userDAO, TokenDAO tokenDAO, ProfileDAO profileDAO, InviteDAO inviteDAO,
        ProfileService profileService) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.profileDAO = profileDAO;
        this.inviteDAO = inviteDAO;
        this.profileService = profileService;
    }

    /**
     * POST /api/wawelauth/register
     * <p>
     * WawelAuth extension endpoint used by the client register dialog.
     * Enforces server registration policy and optional invite consumption.
     */
    public Object register(RequestContext ctx) {
        String username = StringUtil.trimToNull(ctx.requireJsonString("username"));
        String password = ctx.requireJsonString("password");
        String inviteToken = StringUtil.trimToNull(ctx.optJsonString("inviteToken"));

        if (username == null) {
            throw NetException.illegalArgument("Username is required.");
        }
        if (password == null || password.isEmpty()) {
            throw NetException.illegalArgument("Password is required.");
        }

        if (Config.server()
            .getFeatures()
            .isUsernameCheck()) {
            String usernameRegex = Config.server()
                .getRegistration()
                .getPlayerNameRegex();
            if (!username.matches(usernameRegex)) {
                throw NetException.illegalArgument("Invalid username. Must match: " + usernameRegex);
            }
        }

        RegistrationPolicy policy = Config.server()
            .getRegistration()
            .getPolicy();
        if (policy == RegistrationPolicy.CLOSED) {
            throw NetException.forbidden("Registration is disabled on this server.");
        }
        if (policy == RegistrationPolicy.INVITE_ONLY && inviteToken == null) {
            throw NetException.forbidden("Invite token is required.");
        }

        // Hash first so transaction body does only DB work.
        PasswordHasher.HashResult hashResult = PasswordHasher.hash(password);
        long now = System.currentTimeMillis();

        WawelUser user = new WawelUser();
        user.setUuid(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(hashResult.getHash());
        user.setPasswordSalt(hashResult.getSalt());
        user.setCreatedAt(now);

        Set<TextureType> uploadable = EnumSet.noneOf(TextureType.class);
        for (String name : Config.server()
            .getRegistration()
            .getDefaultUploadableTextures()) {
            TextureType type = TextureType.fromApiName(name);
            if (type != null) {
                uploadable.add(type);
            }
        }

        WawelProfile profile = new WawelProfile();
        profile.setUuid(UUID.randomUUID());
        profile.setName(username);
        profile.setOwnerUuid(user.getUuid());
        profile.updateOfflineUuid();
        profile.setUploadableTextures(uploadable);
        profile.setCreatedAt(now);

        WawelServer server = WawelServer.instance();
        if (server == null) {
            throw NetException.forbidden("Server registration module is not available.");
        }

        try {
            server.runInTransaction(() -> {
                if (userDAO.findByUsername(username) != null) {
                    throw new RegistrationFailedException("Username is already taken.");
                }
                if (profileDAO.findByName(username) != null) {
                    throw new RegistrationFailedException("Profile name is already taken.");
                }

                boolean inviteRequired = policy == RegistrationPolicy.INVITE_ONLY;
                boolean inviteProvided = inviteToken != null;
                if (inviteRequired || inviteProvided) {
                    if (inviteToken == null || !inviteDAO.consume(inviteToken)) {
                        throw new RegistrationFailedException("Invalid or exhausted invite token.");
                    }
                }

                userDAO.create(user);
                profileDAO.create(profile);
            });
        } catch (RegistrationFailedException e) {
            throw NetException.forbidden(e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", UuidUtil.toUnsigned(profile.getUuid()));
        response.put("name", profile.getName());
        return response;
    }

    /**
     * POST /api/wawelauth/change-password
     * <p>
     * WawelAuth extension endpoint for account credential management.
     * Requires a valid access token and the current password.
     */
    public Object changePassword(RequestContext ctx) {
        String accessToken = StringUtil.trimToNull(ctx.optJsonString("accessToken"));
        if (accessToken == null) {
            accessToken = StringUtil.trimToNull(ctx.getBearerToken());
        }
        String clientToken = StringUtil.trimToNull(ctx.optJsonString("clientToken"));
        String currentPassword = ctx.requireJsonString("currentPassword");
        String newPassword = ctx.requireJsonString("newPassword");

        if (currentPassword == null || currentPassword.isEmpty()) {
            throw NetException.illegalArgument("Current password is required.");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw NetException.illegalArgument("New password is required.");
        }
        if (currentPassword.equals(newPassword)) {
            throw NetException.illegalArgument("New password must differ from current password.");
        }

        WawelUser user = resolveAuthenticatedUser(accessToken, clientToken);
        if (user.isLocked()) {
            throw NetException.forbidden("Account is locked.");
        }
        if (!PasswordHasher.verify(currentPassword, user.getPasswordHash(), user.getPasswordSalt())) {
            throw NetException.forbidden("Invalid credentials. Invalid username or password.");
        }

        PasswordHasher.HashResult hash = PasswordHasher.hash(newPassword);
        user.setPasswordHash(hash.getHash());
        user.setPasswordSalt(hash.getSalt());
        userDAO.update(user);

        return null; // 204
    }

    /**
     * POST /api/wawelauth/delete-account
     * <p>
     * WawelAuth extension endpoint for self-service account deletion.
     * Requires a valid access token and the current password.
     */
    public Object deleteAccount(RequestContext ctx) {
        String accessToken = StringUtil.trimToNull(ctx.optJsonString("accessToken"));
        if (accessToken == null) {
            accessToken = StringUtil.trimToNull(ctx.getBearerToken());
        }
        String clientToken = StringUtil.trimToNull(ctx.optJsonString("clientToken"));
        String currentPassword = ctx.requireJsonString("currentPassword");

        if (currentPassword == null || currentPassword.isEmpty()) {
            throw NetException.illegalArgument("Current password is required.");
        }

        WawelUser user = resolveAuthenticatedUser(accessToken, clientToken);
        if (user.isLocked()) {
            throw NetException.forbidden("Account is locked.");
        }
        if (!PasswordHasher.verify(currentPassword, user.getPasswordHash(), user.getPasswordSalt())) {
            throw NetException.forbidden("Invalid credentials. Invalid username or password.");
        }

        WawelServer server = WawelServer.instance();
        if (server == null) {
            throw NetException.forbidden("Server registration module is not available.");
        }

        UUID userUuid = user.getUuid();
        server.runInTransaction(() -> {
            tokenDAO.deleteByUser(userUuid);
            List<WawelProfile> profiles = profileDAO.findByOwner(userUuid);
            for (WawelProfile profile : profiles) {
                profileDAO.delete(profile.getUuid());
            }
            userDAO.delete(userUuid);
        });

        return null; // 204
    }

    /**
     * POST /authserver/authenticate
     * <p>
     * Authenticates a user and returns access/client tokens plus available profiles.
     */
    public Object authenticate(RequestContext ctx) {
        String username = ctx.requireJsonString("username");
        String password = ctx.requireJsonString("password");
        String clientToken = ctx.optJsonString("clientToken");
        boolean requestUser = ctx.optJsonBoolean("requestUser", false);

        if (clientToken == null) {
            clientToken = UUID.randomUUID()
                .toString()
                .replace("-", "");
        }

        WawelUser user = userDAO.findByUsername(username);
        if (user == null) {
            throw NetException.forbidden("Invalid credentials. Invalid username or password.");
        }

        if (user.isLocked()) {
            throw NetException.forbidden("Account is locked.");
        }

        if (!PasswordHasher.verify(password, user.getPasswordHash(), user.getPasswordSalt())) {
            throw NetException.forbidden("Invalid credentials. Invalid username or password.");
        }

        // Yggdrasil semantics: authenticating again with the same clientToken for
        // the same user should invalidate previously issued access tokens.
        List<WawelToken> existingTokens = tokenDAO.findByUser(user.getUuid());
        for (WawelToken existing : existingTokens) {
            if (clientToken.equals(existing.getClientToken())) {
                existing.setState(TokenState.INVALID);
                existing.setLastUsedAt(System.currentTimeMillis());
                tokenDAO.update(existing);
            }
        }

        // Create new token
        long now = System.currentTimeMillis();
        WawelToken token = new WawelToken();
        token.setAccessToken(
            UUID.randomUUID()
                .toString()
                .replace("-", ""));
        token.setClientToken(clientToken);
        token.setUserUuid(user.getUuid());
        token.setIssuedAt(now);
        token.setLastUsedAt(now);
        token.setState(TokenState.VALID);

        // Load profiles
        List<WawelProfile> profiles = profileDAO.findByOwner(user.getUuid());

        // Auto-bind if user has exactly one profile
        if (profiles.size() == 1) {
            token.setProfileUuid(
                profiles.get(0)
                    .getUuid());
        }

        // Enforce token cap
        int maxTokens = Config.server()
            .getTokens()
            .getMaxPerUser();
        tokenDAO.evictOldest(user.getUuid(), maxTokens - 1);

        tokenDAO.create(token);

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", token.getAccessToken());
        response.put("clientToken", token.getClientToken());

        // Available profiles
        List<Map<String, Object>> availableProfiles = new ArrayList<>();
        for (WawelProfile p : profiles) {
            availableProfiles.add(profileService.buildSimpleProfile(p));
        }
        response.put("availableProfiles", availableProfiles);

        // Selected profile (if auto-bound)
        if (token.hasProfile()) {
            for (WawelProfile p : profiles) {
                if (p.getUuid()
                    .equals(token.getProfileUuid())) {
                    response.put("selectedProfile", profileService.buildSimpleProfile(p));
                    break;
                }
            }
        }

        if (requestUser) {
            response.put("user", buildUserObject(user));
        }

        return response;
    }

    /**
     * POST /authserver/refresh
     * <p>
     * Refreshes an access token. The old token is invalidated and a new one is issued.
     */
    public Object refresh(RequestContext ctx) {
        String accessToken = ctx.requireJsonString("accessToken");
        String clientToken = ctx.optJsonString("clientToken");
        boolean requestUser = ctx.optJsonBoolean("requestUser", false);

        WawelToken oldToken = tokenDAO.findByTokenPair(accessToken, clientToken);
        if (oldToken == null) {
            throw NetException.forbidden("Invalid token.");
        }

        if (!oldToken.isRefreshable()) {
            throw NetException.forbidden("Token has been invalidated.");
        }

        // Resolve profile binding per spec:
        // - If token has no bound profile and selectedProfile is provided, bind it.
        // - If token already has a bound profile, selectedProfile must be omitted or match.
        UUID selectedProfileUuid = null;
        WawelProfile selectedProfile = null;

        UUID requestedProfileUuid = null;
        if (ctx.getJsonBody()
            .has("selectedProfile")
            && !ctx.getJsonBody()
                .get("selectedProfile")
                .isJsonNull()) {
            String profileId = null;
            try {
                profileId = ctx.getJsonBody()
                    .getAsJsonObject("selectedProfile")
                    .get("id")
                    .getAsString();
            } catch (Exception ignored) {}

            if (profileId != null) {
                try {
                    requestedProfileUuid = UuidUtil.fromUnsigned(profileId);
                } catch (IllegalArgumentException e) {
                    throw NetException.illegalArgument("Invalid profile UUID format.");
                }
            }
        }

        if (oldToken.hasProfile()) {
            // Token already bound: selectedProfile must be omitted or match
            if (requestedProfileUuid != null && !requestedProfileUuid.equals(oldToken.getProfileUuid())) {
                throw NetException.illegalArgument(
                    "Token is already bound to a different profile. "
                        + "selectedProfile must be omitted or match the bound profile.");
            }
            selectedProfileUuid = oldToken.getProfileUuid();
            selectedProfile = profileDAO.findByUuid(selectedProfileUuid);
        } else if (requestedProfileUuid != null) {
            // Token not bound: bind to the requested profile
            selectedProfile = profileDAO.findByUuid(requestedProfileUuid);
            if (selectedProfile == null || !selectedProfile.getOwnerUuid()
                .equals(oldToken.getUserUuid())) {
                throw NetException.forbidden("Invalid profile.");
            }
            selectedProfileUuid = requestedProfileUuid;
        }

        // Invalidate old token
        oldToken.setState(TokenState.INVALID);
        tokenDAO.update(oldToken);

        // Enforce token cap (same as authenticate)
        int maxTokens = Config.server()
            .getTokens()
            .getMaxPerUser();
        tokenDAO.evictOldest(oldToken.getUserUuid(), maxTokens - 1);

        // Create new token
        long now = System.currentTimeMillis();
        WawelToken newToken = new WawelToken();
        newToken.setAccessToken(
            UUID.randomUUID()
                .toString()
                .replace("-", ""));
        newToken.setClientToken(oldToken.getClientToken());
        newToken.setUserUuid(oldToken.getUserUuid());
        newToken.setProfileUuid(selectedProfileUuid);
        newToken.setIssuedAt(now);
        newToken.setLastUsedAt(now);
        newToken.setVersion(oldToken.getVersion() + 1);
        newToken.setState(TokenState.VALID);

        tokenDAO.create(newToken);

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", newToken.getAccessToken());
        response.put("clientToken", newToken.getClientToken());

        if (selectedProfile != null) {
            response.put("selectedProfile", profileService.buildSimpleProfile(selectedProfile));
        }

        if (requestUser) {
            WawelUser user = userDAO.findByUuid(newToken.getUserUuid());
            if (user != null) {
                response.put("user", buildUserObject(user));
            }
        }

        return response;
    }

    /**
     * POST /authserver/validate
     * <p>
     * Validates an access token. Returns 204 on success.
     */
    public Object validate(RequestContext ctx) {
        String accessToken = ctx.requireJsonString("accessToken");
        String clientToken = ctx.optJsonString("clientToken");

        WawelToken token = tokenDAO.findByTokenPair(accessToken, clientToken);
        if (token == null || !token.isUsable()) {
            throw NetException.forbidden("Invalid token.");
        }

        token.setLastUsedAt(System.currentTimeMillis());
        tokenDAO.update(token);

        return null; // 204
    }

    /**
     * POST /authserver/invalidate
     * <p>
     * Invalidates an access token. Always returns 204 (even if token doesn't exist).
     */
    public Object invalidate(RequestContext ctx) {
        String accessToken = ctx.requireJsonString("accessToken");
        String clientToken = ctx.optJsonString("clientToken");

        WawelToken token = tokenDAO.findByTokenPair(accessToken, clientToken);
        if (token != null) {
            token.setState(TokenState.INVALID);
            tokenDAO.update(token);
        }

        return null; // 204
    }

    /**
     * POST /authserver/signout
     * <p>
     * Signs out a user by invalidating all their tokens. Returns 204.
     */
    public Object signout(RequestContext ctx) {
        String username = ctx.requireJsonString("username");
        String password = ctx.requireJsonString("password");

        WawelUser user = userDAO.findByUsername(username);
        if (user == null) {
            throw NetException.forbidden("Invalid credentials. Invalid username or password.");
        }

        if (!PasswordHasher.verify(password, user.getPasswordHash(), user.getPasswordSalt())) {
            throw NetException.forbidden("Invalid credentials. Invalid username or password.");
        }

        tokenDAO.deleteByUser(user.getUuid());

        return null; // 204
    }

    private Map<String, Object> buildUserObject(WawelUser user) {
        Map<String, Object> userObj = new LinkedHashMap<>();
        userObj.put("id", UuidUtil.toUnsigned(user.getUuid()));

        List<Map<String, String>> props = new ArrayList<>();
        if (user.getPreferredLanguage() != null && !user.getPreferredLanguage()
            .isEmpty()) {
            Map<String, String> langProp = new LinkedHashMap<>();
            langProp.put("name", "preferredLanguage");
            langProp.put("value", user.getPreferredLanguage());
            props.add(langProp);
        }
        userObj.put("properties", props);

        return userObj;
    }

    private WawelUser resolveAuthenticatedUser(String accessToken, String clientToken) {
        if (accessToken == null) {
            throw NetException.forbidden("Invalid token.");
        }

        WawelToken token = tokenDAO.findByTokenPair(accessToken, clientToken);
        if (token == null || !token.isUsable()) {
            throw NetException.forbidden("Invalid token.");
        }

        WawelUser user = userDAO.findByUuid(token.getUserUuid());
        if (user == null) {
            throw NetException.forbidden("Invalid token.");
        }
        return user;
    }

    private static class RegistrationFailedException extends RuntimeException {

        RegistrationFailedException(String message) {
            super(message);
        }
    }
}
