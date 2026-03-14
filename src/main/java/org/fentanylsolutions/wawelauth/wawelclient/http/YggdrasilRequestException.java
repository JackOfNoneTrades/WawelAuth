package org.fentanylsolutions.wawelauth.wawelclient.http;

/**
 * Thrown when an Yggdrasil server returns an HTTP error response.
 * <p>
 * Contains the HTTP status code and the Yggdrasil error/errorMessage fields.
 */
public class YggdrasilRequestException extends Exception {

    private final int httpStatus;
    private final String error;
    private final String errorMessage;

    public YggdrasilRequestException(int httpStatus, String error, String errorMessage) {
        super(error + ": " + errorMessage + " (HTTP " + httpStatus + ")");
        this.httpStatus = httpStatus;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Yggdrasil error type, e.g. "ForbiddenOperationException".
     */
    public String getError() {
        return error;
    }

    /**
     * Yggdrasil error message, e.g. "Invalid credentials."
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
