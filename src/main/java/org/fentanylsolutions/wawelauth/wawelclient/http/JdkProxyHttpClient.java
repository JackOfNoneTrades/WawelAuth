package org.fentanylsolutions.wawelauth.wawelclient.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class JdkProxyHttpClient {

    private static final String ALI_HEADER = "X-Authlib-Injector-API-Location";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxResponseBytes;
    private final String userAgent;
    private final boolean available;

    JdkProxyHttpClient(int connectTimeoutMs, int readTimeoutMs, int maxResponseBytes, String userAgent) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxResponseBytes = maxResponseBytes;
        this.userAgent = userAgent;
        this.available = isAvailable();
        if (this.available) {
            enableBasicProxyAuth();
        }
    }

    boolean supports(ProviderProxySettings settings) {
        return available && settings != null
            && settings.isEnabled()
            && settings.hasEndpoint()
            && settings.getType() == ProviderProxyType.HTTP;
    }

    JsonObject postJson(String url, JsonObject body, ProviderProxySettings settings)
        throws IOException, YggdrasilRequestException {
        ResponseData response = execute(
            "POST",
            url,
            mapOf("Content-Type", CONTENT_TYPE_JSON, "Accept", "application/json"),
            body.toString()
                .getBytes(StandardCharsets.UTF_8),
            settings,
            true);
        return parseJsonResponse(response.statusCode, response.bodyAsString());
    }

    JsonObject getJson(String url, ProviderProxySettings settings) throws IOException, YggdrasilRequestException {
        ResponseData response = execute("GET", url, mapOf("Accept", "application/json"), null, settings, true);
        return parseJsonResponse(response.statusCode, response.bodyAsString());
    }

    JsonObject deleteWithAuth(String url, String bearerToken, ProviderProxySettings settings)
        throws IOException, YggdrasilRequestException {
        ResponseData response = execute(
            "DELETE",
            url,
            bearerToken != null && !bearerToken.trim()
                .isEmpty() ? mapOf("Authorization", "Bearer " + bearerToken) : mapOf(),
            null,
            settings,
            true);
        return parseJsonResponse(response.statusCode, response.bodyAsString());
    }

    JsonObject sendMultipart(String method, String url, String bearerToken, String fileField, File file,
        String contentType, Map<String, String> textFields, ProviderProxySettings settings)
        throws IOException, YggdrasilRequestException {
        if (file == null || !file.isFile()) {
            throw new IOException("Upload file does not exist: " + (file != null ? file.getAbsolutePath() : "null"));
        }

        String boundary = "----WawelAuthBoundary" + UUID.randomUUID()
            .toString()
            .replace("-", "");
        byte[] payload = buildMultipartPayload(boundary, fileField, file, contentType, textFields);
        ResponseData response = execute(
            method,
            url,
            bearerToken != null && !bearerToken.trim()
                .isEmpty()
                    ? mapOf(
                        "Authorization",
                        "Bearer " + bearerToken,
                        "Accept",
                        "application/json",
                        "Content-Type",
                        "multipart/form-data; boundary=" + boundary)
                    : mapOf("Accept", "application/json", "Content-Type", "multipart/form-data; boundary=" + boundary),
            payload,
            settings,
            true);
        return parseJsonResponse(response.statusCode, response.bodyAsString());
    }

    String resolveApiRoot(String userUrl, ProviderProxySettings settings) throws IOException {
        String currentUrl = ensureScheme(userUrl);
        for (int hop = 0; hop < 5; hop++) {
            WawelAuth.debug("ALI resolve hop " + hop + ": " + currentUrl);
            ResponseData response = execute(
                "GET",
                currentUrl,
                mapOf("Accept", "application/json"),
                null,
                settings,
                true);

            String aliLocation = response.aliLocation;
            if (aliLocation == null || aliLocation.isEmpty()) {
                break;
            }

            String baseUrl = response.finalUrl != null ? response.finalUrl : currentUrl;
            String resolvedUrl = resolveAbsolute(baseUrl, aliLocation);
            if (stripTrailingSlashes(resolvedUrl).equals(stripTrailingSlashes(baseUrl))) {
                break;
            }

            currentUrl = resolvedUrl;
        }
        return stripTrailingSlashes(currentUrl);
    }

    int probeReachability(String url, ProviderProxySettings settings) throws IOException {
        return execute("GET", url, mapOf("Accept", "*/*"), null, settings, true).statusCode;
    }

    byte[] downloadBytes(String url, ProviderProxySettings settings) throws IOException {
        ResponseData response = execute("GET", url, mapOf("Accept", "*/*"), null, settings, true);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IOException("HTTP " + response.statusCode + " from " + url);
        }
        return response.bodyBytes;
    }

    private ResponseData execute(String method, String url, Map<String, String> headers, byte[] body,
        ProviderProxySettings settings, boolean followRedirects) throws IOException {
        try {
            Class<?> httpClientClass = Class.forName("java.net.http.HttpClient");
            Class<?> clientBuilderClass = Class.forName("java.net.http.HttpClient$Builder");
            Class<?> httpRequestClass = Class.forName("java.net.http.HttpRequest");
            Class<?> httpResponseClass = Class.forName("java.net.http.HttpResponse");
            Class<?> requestBuilderClass = Class.forName("java.net.http.HttpRequest$Builder");
            Class<?> bodyPublishersClass = Class.forName("java.net.http.HttpRequest$BodyPublishers");
            Class<?> bodyPublisherClass = Class.forName("java.net.http.HttpRequest$BodyPublisher");
            Class<?> bodyHandlersClass = Class.forName("java.net.http.HttpResponse$BodyHandlers");
            Class<?> bodyHandlerClass = Class.forName("java.net.http.HttpResponse$BodyHandler");
            Class<?> httpHeadersClass = Class.forName("java.net.http.HttpHeaders");
            Class<?> redirectClass = Class.forName("java.net.http.HttpClient$Redirect");

            Object clientBuilder = httpClientClass.getMethod("newBuilder")
                .invoke(null);
            invoke(clientBuilder, clientBuilderClass, "connectTimeout", Duration.ofMillis(connectTimeoutMs));
            invoke(
                clientBuilder,
                clientBuilderClass,
                "proxy",
                createProxySelector(
                    settings.getHost()
                        .trim(),
                    settings.getPort()
                        .intValue()));
            invoke(clientBuilder, clientBuilderClass, "authenticator", createAuthenticator(settings));
            Object redirectPolicy = Enum.valueOf((Class) redirectClass, followRedirects ? "NORMAL" : "NEVER");
            invoke(clientBuilder, clientBuilderClass, "followRedirects", redirectPolicy);
            Object client = invoke(clientBuilder, clientBuilderClass, "build");

            Object requestBuilder = httpRequestClass.getMethod("newBuilder", URI.class)
                .invoke(null, URI.create(url));
            invoke(requestBuilder, requestBuilderClass, "timeout", Duration.ofMillis(readTimeoutMs));
            header(requestBuilder, requestBuilderClass, "User-Agent", userAgent);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    header(requestBuilder, requestBuilderClass, entry.getKey(), entry.getValue());
                }
            }

            if (body != null) {
                Object publisher = bodyPublishersClass.getMethod("ofByteArray", byte[].class)
                    .invoke(null, new Object[] { body });
                invoke(
                    requestBuilder,
                    requestBuilderClass,
                    "method",
                    method,
                    publisherClass(bodyPublisherClass, publisher));
            } else if ("GET".equals(method)) {
                invoke(requestBuilder, requestBuilderClass, "GET");
            } else if ("DELETE".equals(method)) {
                invoke(requestBuilder, requestBuilderClass, "DELETE");
            } else {
                Object noBody = bodyPublishersClass.getMethod("noBody")
                    .invoke(null);
                invoke(
                    requestBuilder,
                    requestBuilderClass,
                    "method",
                    method,
                    publisherClass(bodyPublisherClass, noBody));
            }

            Object request = invoke(requestBuilder, requestBuilderClass, "build");
            Object bodyHandler = bodyHandlersClass.getMethod("ofByteArray")
                .invoke(null);
            Object response = httpClientClass.getMethod("send", httpRequestClass, bodyHandlerClass)
                .invoke(client, request, bodyHandler);

            int status = (Integer) httpResponseClass.getMethod("statusCode")
                .invoke(response);
            byte[] responseBytes = (byte[]) httpResponseClass.getMethod("body")
                .invoke(response);
            if (responseBytes != null && responseBytes.length > maxResponseBytes) {
                throw new IOException("Response exceeds maximum size of " + maxResponseBytes + " bytes");
            }
            String responseBody = responseBytes != null ? new String(responseBytes, StandardCharsets.UTF_8) : "";
            Object headersObj = httpResponseClass.getMethod("headers")
                .invoke(response);
            String aliLocation = optionalString(
                httpHeadersClass.getMethod("firstValue", String.class)
                    .invoke(headersObj, ALI_HEADER));
            URI finalUri = (URI) httpResponseClass.getMethod("uri")
                .invoke(response);

            WawelAuth.debug("HTTP response " + status + ": " + truncate(responseBody, 200));
            return new ResponseData(
                status,
                responseBytes != null ? responseBytes : new byte[0],
                aliLocation,
                finalUri != null ? finalUri.toString() : null);
        } catch (ClassNotFoundException e) {
            throw new IOException("JDK HttpClient is not available on this JVM.", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread()
                    .interrupt();
                throw new IOException("HTTP request interrupted", cause);
            }
            throw new IOException(cause != null ? cause.getMessage() : e.getMessage(), cause != null ? cause : e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to use JDK HttpClient reflectively", e);
        }
    }

    private static Object publisherClass(Class<?> bodyPublisherClass, Object publisher) {
        return bodyPublisherClass.cast(publisher);
    }

    private static Map<String, String> mapOf() {
        return java.util.Collections.emptyMap();
    }

    private static Map<String, String> mapOf(String k1, String v1) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        map.put(k1, v1);
        return map;
    }

    private static Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static Map<String, String> mapOf(String k1, String v1, String k2, String v2, String k3, String v3) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    private static Authenticator createAuthenticator(final ProviderProxySettings settings) {
        return new Authenticator() {

            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                if (!ProviderProxySupport
                    .matchesProxyRequest(settings, getRequestingHost(), getRequestingSite(), getRequestingPort())) {
                    return null;
                }
                String username = settings.getUsername() != null ? settings.getUsername() : "";
                char[] password = (settings.getPassword() != null ? settings.getPassword() : "").toCharArray();
                return new PasswordAuthentication(username, password);
            }
        };
    }

    private static ProxySelector createProxySelector(final String host, final int port) {
        return new ProxySelector() {

            @Override
            public java.util.List<Proxy> select(URI uri) {
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // HttpClient will surface the original connection error.
            }
        };
    }

    private static Object invoke(Object target, Class<?> ownerType, String methodName, Object... args)
        throws ReflectiveOperationException {
        Method method = findMethod(ownerType, methodName, args);
        if (method == null) {
            throw new NoSuchMethodException(ownerType.getName() + "#" + methodName);
        }
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String methodName, Object... args) {
        Method[] methods = type.getMethods();
        outer: for (Method method : methods) {
            if (!method.getName()
                .equals(methodName)) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != args.length) {
                continue;
            }
            for (int i = 0; i < paramTypes.length; i++) {
                if (args[i] == null) {
                    continue;
                }
                if (!paramTypes[i].isInstance(args[i])
                    && !(paramTypes[i].isPrimitive() && wrapPrimitive(paramTypes[i]).isInstance(args[i]))) {
                    continue outer;
                }
            }
            return method;
        }
        return null;
    }

    private static Class<?> wrapPrimitive(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == char.class) return Character.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        return primitive;
    }

    private static void header(Object requestBuilder, Class<?> requestBuilderClass, String name, String value)
        throws ReflectiveOperationException {
        if (name == null || value == null) return;
        invoke(requestBuilder, requestBuilderClass, "header", name, value);
    }

    private static String optionalString(Object optional) throws ReflectiveOperationException {
        if (!(optional instanceof Optional)) {
            return null;
        }
        Optional<?> opt = (Optional<?>) optional;
        Object value = opt.orElse(null);
        return value != null ? value.toString() : null;
    }

    private JsonObject parseJsonResponse(int status, String responseBody) throws YggdrasilRequestException {
        if (status == 204) {
            return null;
        }
        if (status >= 200 && status < 300) {
            if (responseBody == null || responseBody.isEmpty()) {
                return null;
            }
            return new JsonParser().parse(responseBody)
                .getAsJsonObject();
        }

        String error = "UnknownError";
        String errorMessage = "HTTP " + status;
        try {
            JsonObject errBody = new JsonParser().parse(responseBody)
                .getAsJsonObject();
            if (errBody.has("error")) {
                error = errBody.get("error")
                    .getAsString();
            }
            if (errBody.has("errorMessage")) {
                errorMessage = errBody.get("errorMessage")
                    .getAsString();
            }
        } catch (Exception ignored) {}

        throw new YggdrasilRequestException(status, error, errorMessage);
    }

    private byte[] buildMultipartPayload(String boundary, String fileField, File file, String contentType,
        Map<String, String> textFields) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (textFields != null) {
            for (Map.Entry<String, String> entry : textFields.entrySet()) {
                writeTextPart(out, boundary, entry.getKey(), entry.getValue());
            }
        }
        writeFilePart(out, boundary, fileField, file, contentType);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeTextPart(ByteArrayOutputStream out, String boundary, String fieldName, String value)
        throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
            ("Content-Disposition: form-data; name=\"" + escapeMultipart(fieldName) + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(ByteArrayOutputStream out, String boundary, String fieldName, File file,
        String contentType) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
            ("Content-Disposition: form-data; name=\"" + escapeMultipart(fieldName)
                + "\"; filename=\""
                + escapeMultipart(file.getName())
                + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
            ("Content-Type: " + (contentType != null ? contentType : "application/octet-stream") + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));

        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeMultipart(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private static String ensureScheme(String url) {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private static String resolveAbsolute(String baseUrl, String relativeOrAbsolute) {
        if (relativeOrAbsolute == null || relativeOrAbsolute.isEmpty()) {
            return stripTrailingSlashes(baseUrl);
        }
        URI uri = URI.create(relativeOrAbsolute);
        if (uri.isAbsolute()) {
            return uri.toString();
        }
        return URI.create(baseUrl)
            .resolve(relativeOrAbsolute)
            .toString();
    }

    private static String stripTrailingSlashes(String url) {
        if (url == null) {
            return null;
        }
        String value = url;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isAvailable() {
        try {
            Class.forName("java.net.http.HttpClient");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void enableBasicProxyAuth() {
        allowBasicAuthFor("jdk.http.auth.tunneling.disabledSchemes");
        allowBasicAuthFor("jdk.http.auth.proxying.disabledSchemes");
    }

    private static void allowBasicAuthFor(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null) {
            System.setProperty(propertyName, "");
            return;
        }

        String[] parts = value.split(",");
        StringBuilder updated = new StringBuilder();
        for (String part : parts) {
            String trimmed = part != null ? part.trim() : "";
            if (trimmed.isEmpty() || "basic".equalsIgnoreCase(trimmed)) {
                continue;
            }
            if (updated.length() > 0) {
                updated.append(',');
            }
            updated.append(trimmed);
        }
        System.setProperty(propertyName, updated.toString());
    }

    private static final class ResponseData {

        private final int statusCode;
        private final byte[] bodyBytes;
        private final String aliLocation;
        private final String finalUrl;

        private ResponseData(int statusCode, byte[] bodyBytes, String aliLocation, String finalUrl) {
            this.statusCode = statusCode;
            this.bodyBytes = bodyBytes;
            this.aliLocation = aliLocation;
            this.finalUrl = finalUrl;
        }

        private String bodyAsString() {
            return new String(bodyBytes, StandardCharsets.UTF_8);
        }
    }
}
