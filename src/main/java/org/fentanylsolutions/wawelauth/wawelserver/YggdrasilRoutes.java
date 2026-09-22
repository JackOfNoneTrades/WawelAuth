package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fentanylsolutions.wawelauth.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.KeyManager;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelnet.BinaryResponse;
import org.fentanylsolutions.wawelauth.wawelnet.HttpRouter;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/**
 * Registers all Yggdrasil API routes on the router.
 */
public final class YggdrasilRoutes {

    private YggdrasilRoutes() {}

    public static void register(HttpRouter router, ServerConfig config, KeyManager keyManager, AuthService authService,
        SessionService sessionService, FallbackProxyService fallbackProxyService, TextureService textureService,
        ProfileService profileService, ProfileDAO profileDAO, TextureFileStore textureFileStore) {
        String prefix = config.getApiRoutePrefix();

        // GET apiRoot path: API metadata
        router.get(route(prefix, "/"), ctx -> buildMetadata(config, keyManager));
        if (!prefix.isEmpty()) {
            router.get(prefix + "/", ctx -> buildMetadata(config, keyManager));
        }

        // Auth endpoints
        router.post(route(prefix, "/authserver/authenticate"), authService::authenticate);
        router.post(route(prefix, "/authserver/refresh"), authService::refresh);
        router.post(route(prefix, "/authserver/validate"), authService::validate);
        router.post(route(prefix, "/authserver/invalidate"), authService::invalidate);
        router.post(route(prefix, "/authserver/signout"), authService::signout);
        router.post(route(prefix, "/api/wawelauth/register"), authService::register);
        router.post(route(prefix, "/api/wawelauth/change-password"), authService::changePassword);
        router.post(route(prefix, "/api/wawelauth/delete-account"), authService::deleteAccount);

        // Session endpoints
        router.post(route(prefix, "/sessionserver/session/minecraft/join"), sessionService::join);
        router.get(route(prefix, "/sessionserver/session/minecraft/hasJoined"), sessionService::hasJoined);
        router.get(route(prefix, "/sessionserver/session/minecraft/profile/{uuid}"), sessionService::profileByUuid);
        router.get(route(prefix, "/api/users/profiles/minecraft/{name}"), ctx -> {
            String name = ctx.getPathParam("name");
            if (name == null || name.trim()
                .isEmpty()) {
                throw NetException.illegalArgument("Player name is required.");
            }
            WawelProfile profile = profileDAO.findByName(name);
            if (profile == null) {
                throw NetException.notFound("Profile not found.");
            }
            return profileService.buildSimpleProfile(profile);
        });

        // Vanilla/Yggdrasil compatibility stubs.
        router.get(route(prefix, "/api/user/security/location"), ctx -> null); // 204
        router.get(
            route(prefix, "/blockedservers"),
            ctx -> new BinaryResponse(new byte[0], "text/plain; charset=utf-8", new LinkedHashMap<>()));

        // Bulk name lookup
        router.post(route(prefix, "/api/profiles/minecraft"), ctx -> {
            String body = ctx.getRequest()
                .content()
                .toString(io.netty.util.CharsetUtil.UTF_8);
            JsonArray names;
            try {
                names = new JsonParser().parse(body)
                    .getAsJsonArray();
            } catch (Exception e) {
                throw NetException.illegalArgument("Request body must be a JSON array of player names.");
            }

            List<String> nameList = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                try {
                    nameList.add(
                        names.get(i)
                            .getAsString());
                } catch (Exception e) {
                    throw NetException.illegalArgument("Array element at index " + i + " is not a string.");
                }
            }

            if (nameList.size() > 10) {
                throw NetException.illegalArgument("At most 10 names are allowed per request.");
            }

            List<WawelProfile> profiles = profileDAO.findByNames(nameList);
            List<Map<String, Object>> result = new ArrayList<>();
            for (WawelProfile p : profiles) {
                result.add(profileService.buildSimpleProfile(p));
            }
            return result;
        });

        // Texture upload/delete
        router.put(route(prefix, "/api/user/profile/{uuid}/{textureType}"), textureService::uploadTexture);
        router.delete(route(prefix, "/api/user/profile/{uuid}/{textureType}"), textureService::deleteTexture);

        // Texture file serving
        router.get(route(prefix, "/textures/{hash}"), ctx -> {
            String hash = ctx.getPathParam("hash");

            // Validate hash is hex-only to prevent path traversal
            if (!hash.matches("[0-9a-f]{64}")) {
                throw NetException.notFound("Invalid texture hash.");
            }

            byte[] data = textureFileStore.read(hash);
            if (data == null) {
                throw NetException.notFound("Texture not found.");
            }

            String contentType = (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') ? "image/gif"
                : "image/png";
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Cache-Control", "public, max-age=86400");
            return new BinaryResponse(data, contentType, headers);
        });

        // Upstream texture proxy (used by fallback profile forwarding)
        router.get(route(prefix, "/textures/proxy/{fallbackKey}/{encodedUrl}"), fallbackProxyService::proxyTexture);
    }

    private static String route(String prefix, String path) {
        if (prefix == null || prefix.isEmpty() || "/".equals(prefix)) {
            return path;
        }
        if ("/".equals(path)) {
            return prefix;
        }
        return prefix + path;
    }

    private static Map<String, Object> buildMetadata(ServerConfig config, KeyManager keyManager) {
        Map<String, Object> root = new LinkedHashMap<>();

        // Meta section
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(
            "implementationName",
            config.getMeta()
                .getImplementationName());
        meta.put(
            "implementationVersion",
            config.getMeta()
                .getImplementationVersion());
        meta.put("serverName", config.getServerName());

        // Links
        Map<String, String> links = new LinkedHashMap<>();
        String homepage = config.getMeta()
            .getServerHomepage();
        if (homepage != null && !homepage.isEmpty()) {
            links.put("homepage", homepage);
        }
        String register = config.getMeta()
            .getServerRegister();
        if (register != null && !register.isEmpty()) {
            links.put("register", register);
        }
        if (!links.isEmpty()) {
            meta.put("links", links);
        }

        // Feature flags
        Map<String, Boolean> features = new LinkedHashMap<>();
        ServerConfig.Features f = config.getFeatures();
        // This implementation only supports username-based login.
        features.put("non_email_login", true);
        features.put("legacy_skin_api", f.isLegacySkinApi());
        features.put("no_mojang_namespace", f.isNoMojangNamespace());
        // 1.7.10 target: profile keys and modern anti-features are not supported.
        features.put("enable_profile_key", false);
        features.put("enable_mojang_anti_features", false);
        features.put("username_check", f.isUsernameCheck());
        meta.put("feature", features);

        root.put("meta", meta);

        // Skin domains (local + fallback domains)
        root.put("skinDomains", collectMetadataSkinDomains(config));

        // Signature public key: PEM format (authlib-injector requires the headers
        // despite the spec saying "base64-encoded DER")
        root.put(
            "signaturePublickey",
            "-----BEGIN PUBLIC KEY-----\n" + keyManager.getPublicKeyBase64() + "\n-----END PUBLIC KEY-----");

        return root;
    }

    private static List<String> collectMetadataSkinDomains(ServerConfig config) {
        List<String> domains = new ArrayList<>();
        if (config == null) {
            return domains;
        }

        for (String domain : config.getSkinDomains()) {
            addSkinDomain(domains, domain);
        }

        for (FallbackServer fallback : config.getFallbackServers()) {
            if (fallback == null) continue;
            for (String domain : fallback.getSkinDomains()) {
                addSkinDomain(domains, domain);
            }
        }

        return domains;
    }

    private static void addSkinDomain(List<String> domains, String domain) {
        if (domain == null) return;
        String trimmed = domain.trim();
        if (trimmed.isEmpty()) return;
        if (!domains.contains(trimmed)) {
            domains.add(trimmed);
        }
    }

}
