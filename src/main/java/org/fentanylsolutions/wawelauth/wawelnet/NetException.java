package org.fentanylsolutions.wawelauth.wawelnet;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Exception carrying Yggdrasil error semantics.
 * <p>
 * Thrown from route handlers, caught by {@link HttpRequestHandler}
 * and serialized as {@code {"error":"...", "errorMessage":"..."}}.
 */
public class NetException extends RuntimeException {

    private final HttpResponseStatus httpStatus;
    private final String errorType;
    private final String errorMessage;

    public NetException(HttpResponseStatus httpStatus, String errorType, String errorMessage) {
        super(errorType + ": " + errorMessage);
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public HttpResponseStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static NetException forbidden(String message) {
        return new NetException(HttpResponseStatus.FORBIDDEN, "ForbiddenOperationException", message);
    }

    public static NetException illegalArgument(String message) {
        return new NetException(HttpResponseStatus.BAD_REQUEST, "IllegalArgumentException", message);
    }

    public static NetException notFound(String message) {
        return new NetException(HttpResponseStatus.NOT_FOUND, "NotFoundOperationException", message);
    }

    /**
     * Alias for {@link #forbidden}: Yggdrasil uses 403 (not 401) for auth failures.
     * Kept as a separate method for call-site readability where the intent is "unauthorized".
     */
    public static NetException unauthorized(String message) {
        return forbidden(message);
    }

    public static NetException methodNotAllowed() {
        return new NetException(
            HttpResponseStatus.METHOD_NOT_ALLOWED,
            "MethodNotAllowedException",
            "Method not allowed");
    }
}
