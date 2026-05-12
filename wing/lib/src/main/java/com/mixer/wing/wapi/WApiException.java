package com.mixer.wing.wapi;

/**
 * WING wapi 通訊層例外
 */
public class WApiException extends Exception {
    private final int errorCode;

    public WApiException(String message) {
        super(message);
        this.errorCode = -1;
    }

    public WApiException(String message, int errorCode) {
        super(message + " (error code: " + errorCode + ")");
        this.errorCode = errorCode;
    }

    public WApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    public int getErrorCode() {
        return errorCode;
    }
}