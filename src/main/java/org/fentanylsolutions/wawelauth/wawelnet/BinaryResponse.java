package org.fentanylsolutions.wawelauth.wawelnet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wrapper for non-JSON responses (e.g. texture images).
 * <p>
 * {@link HttpRequestHandler} writes the raw bytes with the given content type
 * and any extra headers.
 */
public class BinaryResponse {

    private final byte[] data;
    private final String contentType;
    private final Map<String, String> headers;

    public BinaryResponse(byte[] data, String contentType) {
        this(data, contentType, new LinkedHashMap<>());
    }

    public BinaryResponse(byte[] data, String contentType, Map<String, String> headers) {
        this.data = data;
        this.contentType = contentType;
        this.headers = headers;
    }

    public byte[] getData() {
        return data;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
