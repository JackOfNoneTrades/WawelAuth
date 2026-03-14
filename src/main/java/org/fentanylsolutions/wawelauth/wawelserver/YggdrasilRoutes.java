package org.fentanylsolutions.wawelauth.wawelserver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
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
        if (!prefix.isEmpty()) {
            router.get("/", ctx -> buildLandingPage(config, prefix));
        }

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

    private static BinaryResponse buildLandingPage(ServerConfig config, String apiPrefix) {
        String serverName = escapeHtml(config.getServerName());
        String implementationName = escapeHtml(
            config.getMeta()
                .getImplementationName());
        String description = escapeHtml(
            config.getMeta()
                .getPublicDescription());
        String apiRoot = escapeHtml(config.getApiRoot());
        String homepage = trimToNull(
            config.getMeta()
                .getServerHomepage());
        String register = trimToNull(
            config.getMeta()
                .getServerRegister());

        StringBuilder html = new StringBuilder(2048);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>")
            .append(serverName)
            .append("</title><style>")
            .append("body{margin:0;font-family:Segoe UI,Inter,sans-serif;background:#0b1120;color:#ecf2ff;")
            .append("background-image:radial-gradient(circle at 20% 10%,#20305f 0,transparent 44%),")
            .append(
                "radial-gradient(circle at 85% 20%,#184970 0,transparent 35%),linear-gradient(160deg,#070b15 0,#0d1328 55%,#0a0e1d 100%);}")
            .append(".page{max-width:860px;margin:0 auto;padding:48px 20px 40px;}")
            .append(".card{background:rgba(10,18,40,.86);border:1px solid rgba(107,140,230,.34);border-radius:18px;")
            .append("box-shadow:0 12px 38px rgba(4,10,20,.42);padding:28px 30px;}")
            .append("h1{margin:0 0 8px;font-size:2rem}.sub{margin:0;color:#a9b9df;font-size:1.02rem}")
            .append(
                "p{line-height:1.65;color:#d9e3ff}code{background:rgba(255,255,255,.08);padding:.16rem .38rem;border-radius:7px}")
            .append(
                ".actions{display:flex;flex-wrap:wrap;gap:12px;margin-top:22px}.btn{display:inline-flex;align-items:center;")
            .append(
                "padding:.72rem 1rem;border-radius:12px;border:1px solid rgba(104,143,236,.52);color:#ecf2ff;text-decoration:none;")
            .append("background:linear-gradient(160deg,rgba(33,57,117,.95) 0,rgba(16,32,72,.98) 100%)}")
            .append(
                ".btn.alt{background:transparent}.meta{margin-top:20px;color:#a9b9df;font-size:.95rem}</style></head><body><main class=\"page\">")
            .append("<section class=\"card\"><h1>")
            .append(serverName)
            .append("</h1><p class=\"sub\">")
            .append(implementationName)
            .append("</p>");

        if (!description.isEmpty()) {
            html.append("<p>")
                .append(description.replace("\n", "<br>"))
                .append("</p>");
        }

        html.append("<p class=\"meta\">Authentication API root: <code>")
            .append(apiRoot)
            .append("</code> (mounted at <code>")
            .append(escapeHtml(apiPrefix))
            .append("</code>)</p>");

        if (homepage != null || register != null) {
            html.append("<div class=\"actions\">");
            if (homepage != null) {
                html.append("<a class=\"btn alt\" href=\"")
                    .append(escapeHtml(homepage))
                    .append("\">Homepage</a>");
            }
            if (register != null) {
                html.append("<a class=\"btn\" href=\"")
                    .append(escapeHtml(register))
                    .append("\">Register</a>");
            }
            html.append("</div>");
        }

        html.append("</section></main></body></html>");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Pragma", "no-cache");
        return new BinaryResponse(
            html.toString()
                .getBytes(StandardCharsets.UTF_8),
            "text/html; charset=utf-8",
            headers);
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
