package org.fentanylsolutions.wawelauth.wawelnet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

import com.google.common.net.InetAddresses;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

/**
 * Wraps a Netty {@link FullHttpRequest} with path parameters and
 * convenience accessors for Yggdrasil-style JSON bodies.
 */
public class RequestContext {

    private final FullHttpRequest request;
    private final Map<String, String> pathParams;
    private final SocketAddress remoteAddress;
    private final boolean secureTransport;

    private JsonObject cachedBody;
    private boolean bodyParsed;

    public RequestContext(FullHttpRequest request, Map<String, String> pathParams, SocketAddress remoteAddress,
        boolean secureTransport) {
        this.request = request;
        this.pathParams = pathParams;
        this.remoteAddress = remoteAddress;
        this.secureTransport = secureTransport;
    }

    public FullHttpRequest getRequest() {
        return request;
    }

    /**
     * Lazily parses and caches the request body as a JSON object.
     *
     * @throws NetException 400 if the body is not valid JSON or is empty
     */
    public JsonObject getJsonBody() {
        if (!bodyParsed) {
            bodyParsed = true;
            String raw = request.content()
                .toString(CharsetUtil.UTF_8);
            if (raw.isEmpty()) {
                throw NetException.illegalArgument("Request body is empty");
            }
            try {
                cachedBody = new JsonParser().parse(raw)
                    .getAsJsonObject();
            } catch (JsonSyntaxException | IllegalStateException e) {
                throw NetException.illegalArgument("Invalid JSON in request body");
            }
        }
        return cachedBody;
    }

    /**
     * Gets a required string field from the JSON body.
     *
     * @throws NetException 400 if the field is missing or not a string
     */
    public String requireJsonString(String field) {
        JsonObject body = getJsonBody();
        if (!body.has(field) || body.get(field)
            .isJsonNull()) {
            throw NetException.illegalArgument("Missing required field: " + field);
        }
        try {
            return body.get(field)
                .getAsString();
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be a string");
        }
    }

    /**
     * Gets an optional string field from the JSON body, or null if absent.
     */
    public String optJsonString(String field) {
        JsonObject body = getJsonBody();
        if (!body.has(field) || body.get(field)
            .isJsonNull()) {
            return null;
        }
        try {
            return body.get(field)
                .getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets an optional boolean field from the JSON body.
     */
    public boolean optJsonBoolean(String field, boolean defaultValue) {
        JsonObject body = getJsonBody();
        if (!body.has(field) || body.get(field)
            .isJsonNull()) {
            return defaultValue;
        }
        try {
            return body.get(field)
                .getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a query parameter from the URI, or null if absent.
     */
    public String getQueryParam(String name) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
        List<String> values = decoder.parameters()
            .get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    /**
     * Gets a path parameter extracted by the router (e.g. {@code {uuid}}).
     */
    public String getPathParam(String name) {
        return pathParams.get(name);
    }

    /**
     * Extracts a Bearer token from the Authorization header.
     *
     * @return the token string, or null if no Bearer token is present
     */
    public String getBearerToken() {
        String auth = request.headers()
            .get(HttpHeaders.Names.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7)
                .trim();
        }
        return null;
    }

    /**
     * Returns the client's IP address as a string.
     */
    public String getClientIp() {
        String remoteIp = getRemoteIp();
        if (!isTrustedForwardingPeer(remoteIp)) {
            return remoteIp;
        }

        String forwardedIp = getForwardedClientIp();
        return forwardedIp == null ? remoteIp : forwardedIp;
    }

    /**
     * Returns the raw TCP peer IP, without forwarded header handling.
     */
    public String getRemoteIp() {
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) remoteAddress;
            InetAddress address = inet.getAddress();
            return address == null ? inet.getHostString() : address.getHostAddress();
        }
        return remoteAddress.toString();
    }

    /**
     * Returns true when the request reached the embedded server over native TLS.
     */
    public boolean isSecureTransport() {
        return secureTransport;
    }

    /**
     * Returns the raw request URI path (without query string).
     */
    public String getPath() {
        QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
        return decoder.path();
    }

    private String getForwardedClientIp() {
        String realIp = normalizeForwardedIp(
            request.headers()
                .get("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        String forwardedFor = request.headers()
            .get("X-Forwarded-For");
        if (forwardedFor == null) {
            return null;
        }

        String[] parts = forwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = normalizeForwardedIp(parts[i]);
            if (ip != null) {
                return ip;
            }
        }
        return null;
    }

    private static boolean isTrustedForwardingPeer(String remoteIp) {
        try {
            return InetAddresses.forString(remoteIp)
                .isLoopbackAddress();
        } catch (IllegalArgumentException e) {
            try {
                return InetAddress.getByName(remoteIp)
                    .isLoopbackAddress();
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private static String normalizeForwardedIp(String value) {
        if (value == null) {
            return null;
        }

        String token = value.trim();
        if (token.isEmpty()) {
            return null;
        }
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1)
                .trim();
        }
        if (token.startsWith("[") && token.contains("]")) {
            token = token.substring(1, token.indexOf(']'));
        } else {
            int colon = token.lastIndexOf(':');
            if (colon > 0 && token.indexOf(':') == colon && token.indexOf('.') >= 0) {
                token = token.substring(0, colon);
            }
        }

        try {
            return InetAddresses.toAddrString(InetAddresses.forString(token));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
