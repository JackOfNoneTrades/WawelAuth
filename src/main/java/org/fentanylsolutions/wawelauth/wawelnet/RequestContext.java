package org.fentanylsolutions.wawelauth.wawelnet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

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
        if (remoteAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) remoteAddress).getAddress()
                .getHostAddress();
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
}
