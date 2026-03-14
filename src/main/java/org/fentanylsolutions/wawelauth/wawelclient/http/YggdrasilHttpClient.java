package org.fentanylsolutions.wawelauth.wawelclient.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Simple HTTP client for making Yggdrasil API requests to remote auth servers.
 * Uses {@link java.net.HttpURLConnection} (always available in MC 1.7.10).
 * <p>
 * All methods are synchronous and blocking. The caller is responsible for
 * running them off the main thread if needed.
 */
public class YggdrasilHttpClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024; // 1 MB
    private static final int MAX_ALI_HOPS = 5;
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final String ALI_HEADER = "X-Authlib-Injector-API-Location";
    private static final String USER_AGENT = "WawelAuth";

    private final JdkProxyHttpClient jdkProxyHttpClient = new JdkProxyHttpClient(
        CONNECT_TIMEOUT_MS,
        READ_TIMEOUT_MS,
        MAX_RESPONSE_BYTES,
        USER_AGENT);

    /**
     * Perform a JSON POST request.
     *
     * @param url  full URL (e.g. "https://authserver.mojang.com/authenticate")
     * @param body JSON request body
     * @return parsed response, or null for 204 No Content
     * @throws YggdrasilRequestException on HTTP error responses (403, 400, etc.)
     * @throws IOException               on network errors (unreachable, timeout, etc.)
     */
    public JsonObject postJson(String url, JsonObject body) throws IOException, YggdrasilRequestException {
        return postJson(null, url, body);
    }

    public JsonObject postJson(ClientProvider provider, String url, JsonObject body)
        throws IOException, YggdrasilRequestException {
        byte[] bodyBytes = body.toString()
            .getBytes(StandardCharsets.UTF_8);

        ProviderProxySettings settings = settingsFor(provider);
        if (jdkProxyHttpClient.supports(settings)) {
            debugRequest("POST", provider, url, settings, "jdk-http-client");
            return jdkProxyHttpClient.postJson(url, body, settings);
        }
        debugRequest("POST", provider, url, settings, "urlconnection");
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(settings)) {
            HttpURLConnection conn = openConnection(url, settings);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                return handleResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    /**
     * Perform a JSON GET request.
     *
     * @param url full URL
     * @return parsed response
     * @throws YggdrasilRequestException on HTTP error responses
     * @throws IOException               on network errors
     */
    public JsonObject getJson(String url) throws IOException, YggdrasilRequestException {
        return getJson(null, url);
    }

    public JsonObject getJson(ClientProvider provider, String url) throws IOException, YggdrasilRequestException {
        ProviderProxySettings settings = settingsFor(provider);
        if (jdkProxyHttpClient.supports(settings)) {
            debugRequest("GET", provider, url, settings, "jdk-http-client");
            return jdkProxyHttpClient.getJson(url, settings);
        }
        debugRequest("GET", provider, url, settings, "urlconnection");
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(settings)) {
            HttpURLConnection conn = openConnection(url, settings);
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                return handleResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    /**
     * Perform a multipart/form-data PUT request.
     *
     * @param url         full endpoint URL
     * @param bearerToken access token for Authorization header (nullable)
     * @param fileField   multipart file field name, usually "file"
     * @param file        file to upload
     * @param contentType file content type, e.g. "image/png"
     * @param textFields  additional text multipart fields (nullable)
     * @return parsed response, or null for 204 No Content
     */
    public JsonObject putMultipart(String url, String bearerToken, String fileField, File file, String contentType,
        Map<String, String> textFields) throws IOException, YggdrasilRequestException {
        return putMultipart(null, url, bearerToken, fileField, file, contentType, textFields);
    }

    public JsonObject putMultipart(ClientProvider provider, String url, String bearerToken, String fileField, File file,
        String contentType, Map<String, String> textFields) throws IOException, YggdrasilRequestException {
        return sendMultipart("PUT", provider, url, bearerToken, fileField, file, contentType, textFields);
    }

    /**
     * Perform a multipart/form-data POST request.
     *
     * @param url         full endpoint URL
     * @param bearerToken access token for Authorization header (nullable)
     * @param fileField   multipart file field name, usually "file"
     * @param file        file to upload
     * @param contentType file content type, e.g. "image/png"
     * @param textFields  additional text multipart fields (nullable)
     * @return parsed response, or null for 204 No Content
     */
    public JsonObject postMultipart(String url, String bearerToken, String fileField, File file, String contentType,
        Map<String, String> textFields) throws IOException, YggdrasilRequestException {
        return postMultipart(null, url, bearerToken, fileField, file, contentType, textFields);
    }

    public JsonObject postMultipart(ClientProvider provider, String url, String bearerToken, String fileField,
        File file, String contentType, Map<String, String> textFields) throws IOException, YggdrasilRequestException {
        return sendMultipart("POST", provider, url, bearerToken, fileField, file, contentType, textFields);
    }

    /**
     * Perform an authenticated DELETE request.
     *
     * @param url         full endpoint URL
     * @param bearerToken access token for Authorization header (nullable)
     * @return parsed response, or null for 204 No Content
     * @throws YggdrasilRequestException on HTTP error responses
     * @throws IOException               on network errors
     */
    public JsonObject deleteWithAuth(String url, String bearerToken) throws IOException, YggdrasilRequestException {
        return deleteWithAuth(null, url, bearerToken);
    }

    public JsonObject deleteWithAuth(ClientProvider provider, String url, String bearerToken)
        throws IOException, YggdrasilRequestException {
        ProviderProxySettings settings = settingsFor(provider);
        if (jdkProxyHttpClient.supports(settings)) {
            debugRequest("DELETE", provider, url, settings, "jdk-http-client");
            return jdkProxyHttpClient.deleteWithAuth(url, bearerToken, settings);
        }
        debugRequest("DELETE", provider, url, settings, "urlconnection");
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(settings)) {
            HttpURLConnection conn = openConnection(url, settings);
            try {
                conn.setRequestMethod("DELETE");
                if (bearerToken != null && !bearerToken.trim()
                    .isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
                }
                return handleResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    private JsonObject sendMultipart(String method, ClientProvider provider, String url, String bearerToken,
        String fileField, File file, String contentType, Map<String, String> textFields)
        throws IOException, YggdrasilRequestException {
        if (file == null || !file.isFile()) {
            throw new IOException("Upload file does not exist: " + (file != null ? file.getAbsolutePath() : "null"));
        }

        WawelAuth.debug("HTTP " + method + " multipart " + url + " (file: " + file.getName() + ")");

        String boundary = "----WawelAuthBoundary" + UUID.randomUUID()
            .toString()
            .replace("-", "");
        ProviderProxySettings settings = settingsFor(provider);
        if (jdkProxyHttpClient.supports(settings)) {
            debugRequest(method, provider, url, settings, "jdk-http-client");
            return jdkProxyHttpClient
                .sendMultipart(method, url, bearerToken, fileField, file, contentType, textFields, settings);
        }
        debugRequest(method, provider, url, settings, "urlconnection");
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(settings)) {
            HttpURLConnection conn = openConnection(url, settings);
            try {
                conn.setRequestMethod(method);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Accept", "application/json");
                if (bearerToken != null && !bearerToken.trim()
                    .isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
                }
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    if (textFields != null) {
                        for (Map.Entry<String, String> entry : textFields.entrySet()) {
                            writeTextPart(os, boundary, entry.getKey(), entry.getValue());
                        }
                    }
                    writeFilePart(os, boundary, fileField, file, contentType);
                    os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                return handleResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    /**
     * Resolve the true API root URL by following the ALI
     * (Authlib-Injector-API-Location) header.
     *
     * <ol>
     * <li>GET the user-provided URL</li>
     * <li>If the response contains X-Authlib-Injector-API-Location, resolve
     * it to an absolute URL</li>
     * <li>If the resolved URL differs from the current URL, repeat (up to 5 hops)</li>
     * <li>Return the final URL, normalized (trailing slash stripped)</li>
     * </ol>
     *
     * @param userUrl the URL provided by the user
     * @return normalized API root URL
     * @throws IOException on network errors
     */
    public String resolveApiRoot(String userUrl) throws IOException {
        return resolveApiRoot(userUrl, null);
    }

    public String resolveApiRoot(String userUrl, ProviderProxySettings settings) throws IOException {
        if (jdkProxyHttpClient.supports(settings)) {
            debugRequest("GET", null, userUrl, settings, "jdk-http-client");
            return jdkProxyHttpClient.resolveApiRoot(userUrl, settings);
        }
        // Only ensure scheme: do NOT strip trailing slash yet, as URI.resolve()
        // needs it to correctly resolve relative paths:
        // URI("https://ex.com/api/").resolve("other") → "https://ex.com/api/other"
        // URI("https://ex.com/api").resolve("other") → "https://ex.com/other"
        String currentUrl = ensureScheme(userUrl);

        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(settings)) {
            for (int hop = 0; hop < MAX_ALI_HOPS; hop++) {
                WawelAuth.debug("ALI resolve hop " + hop + ": " + currentUrl);

                HttpURLConnection conn = openConnection(currentUrl, settings);
                try {
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setInstanceFollowRedirects(true);

                    // We don't care about the response body here, just the header
                    conn.getResponseCode();

                    String aliLocation = conn.getHeaderField(ALI_HEADER);
                    if (aliLocation == null || aliLocation.isEmpty()) {
                        // No ALI header: current URL is the API root
                        break;
                    }

                    // Resolve against current URL (which may have trailing slash)
                    String resolvedUrl = resolveAbsolute(currentUrl, aliLocation);

                    // Compare after stripping trailing slash to detect self-references
                    if (stripTrailingSlashes(resolvedUrl).equals(stripTrailingSlashes(currentUrl))) {
                        break;
                    }

                    currentUrl = resolvedUrl;
                } finally {
                    conn.disconnect();
                }
            }
        }

        // Only strip trailing slash on the final return value
        return stripTrailingSlashes(currentUrl);
    }

    public int probeReachability(String url, ProviderProxySettings settings) throws IOException {
        if (jdkProxyHttpClient.supports(settings)) {
            debugRequest("PROBE", null, url, settings, "jdk-http-client");
            return jdkProxyHttpClient.probeReachability(url, settings);
        }
        debugRequest("PROBE", null, url, settings, "urlconnection");
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(settings)) {
            HttpURLConnection conn = openConnection(url, settings);
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "*/*");
                return conn.getResponseCode();
            } finally {
                conn.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(String url, ProviderProxySettings settings) throws IOException {
        return ProviderProxySupport.openConnection(url, settings, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, USER_AGENT);
    }

    private static ProviderProxySettings settingsFor(ClientProvider provider) {
        return provider != null ? provider.getProxySettings() : null;
    }

    private static void debugRequest(String method, ClientProvider provider, String url, ProviderProxySettings settings,
        String transport) {
        WawelAuth.debug(
            "HTTP " + method
                + " "
                + url
                + " [provider="
                + (provider != null ? provider.getName() : "-")
                + ", proxy="
                + ProviderProxySupport.describeProxySettings(settings)
                + ", transport="
                + transport
                + "]");
    }

    private JsonObject handleResponse(HttpURLConnection conn) throws IOException, YggdrasilRequestException {
        int status = conn.getResponseCode();

        if (status == 204) {
            return null;
        }

        InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

        String responseBody;
        if (stream != null) {
            responseBody = readStream(stream);
        } else {
            responseBody = "";
        }

        WawelAuth.debug("HTTP response " + status + ": " + truncate(responseBody, 200));

        if (status >= 200 && status < 300) {
            if (responseBody.isEmpty()) {
                return null;
            }
            return new JsonParser().parse(responseBody)
                .getAsJsonObject();
        }

        // Parse Yggdrasil error response
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
        } catch (Exception ignored) {
            // Response wasn't valid JSON: use defaults
        }

        throw new YggdrasilRequestException(status, error, errorMessage);
    }

    private static String readStream(InputStream stream) throws IOException {
        try (InputStream is = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int totalRead = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                totalRead += n;
                if (totalRead > MAX_RESPONSE_BYTES) {
                    throw new IOException("Response exceeds maximum size of " + MAX_RESPONSE_BYTES + " bytes");
                }
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static void writeTextPart(OutputStream os, String boundary, String fieldName, String value)
        throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(
            ("Content-Disposition: form-data; name=\"" + escapeMultipart(fieldName) + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        os.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        os.write((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
        os.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(OutputStream os, String boundary, String fieldName, File file, String contentType)
        throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(
            ("Content-Disposition: form-data; name=\"" + escapeMultipart(fieldName)
                + "\"; filename=\""
                + escapeMultipart(file.getName())
                + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(
            ("Content-Type: " + (contentType != null ? contentType : "application/octet-stream") + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));

        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
        }

        os.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeMultipart(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");
    }

    /**
     * Resolve a potentially relative URL against a base URL.
     */
    private static String resolveAbsolute(String baseUrl, String relativeOrAbsolute) {
        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(relativeOrAbsolute);
            return resolved.toString();
        } catch (Exception e) {
            // Fallback: treat as absolute
            return relativeOrAbsolute;
        }
    }

    /**
     * Ensure a URL has a scheme (defaults to https).
     */
    static String ensureScheme(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    /**
     * Strip trailing slashes from a URL.
     */
    static String stripTrailingSlashes(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
