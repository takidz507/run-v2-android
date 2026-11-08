package com.takidz.runv2.model;

/**
 * Typed result returned by every MatrixClient / store operation.
 *
 * <p>Rule of this layer: network code NEVER throws a raw exception to the UI.
 * Every public method returns a {@code Result} — either success with a value,
 * or failure with a human-safe message plus a machine-readable error code.
 *
 * <p>Error codes used across the layer:
 * <ul>
 *   <li>{@code OFFLINE} — client flagged offline, or no route to host / DNS failure.</li>
 *   <li>{@code TIMEOUT} — connect / read timeout.</li>
 *   <li>{@code NETWORK} — any other {@link java.io.IOException}.</li>
 *   <li>{@code HTTP_xxx} — HTTP status with no Matrix errcode (e.g. {@code HTTP_500}).</li>
 *   <li>{@code M_*} — Matrix errcode from the server body, e.g. {@code M_FORBIDDEN},
 *       {@code M_NOT_FOUND}, {@code M_UNKNOWN_TOKEN}, {@code M_LIMIT_EXCEEDED}.</li>
 *   <li>{@code NOT_LOGGED_IN} — call needs an access token and there is none.</li>
 *   <li>{@code INVALID_ARGUMENT} — caller passed a null / blank / malformed argument.</li>
 *   <li>{@code PARSE_ERROR} — server reply was not valid JSON / unexpected shape.</li>
 *   <li>{@code CRYPTO_ERROR} — PIN-backup encrypt/decrypt failure (wrong PIN or corrupt blob).</li>
 * </ul>
 *
 * @param <T> type of the success value; use {@link Void} for calls with no payload.
 */
public final class Result<T> {

    private final boolean success;
    private final T value;
    private final String errorMessage;
    private final String errorCode;
    private final int httpStatus;

    private Result(boolean success, T value, String errorMessage, String errorCode, int httpStatus) {
        this.success = success;
        this.value = value;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /** Success result carrying {@code value} (may be null for {@code Void} results). */
    public static <T> Result<T> ok(T value) {
        return new Result<>(true, value, null, null, 200);
    }

    /** Failure result. {@code errorMessage} must be safe to show / log (never contains a password, PIN or token). */
    public static <T> Result<T> error(String errorCode, String errorMessage) {
        return new Result<>(false, null, errorMessage, errorCode, 0);
    }

    /** Failure result with the HTTP status that produced it. */
    public static <T> Result<T> error(String errorCode, String errorMessage, int httpStatus) {
        return new Result<>(false, null, errorMessage, errorCode, httpStatus);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isError() {
        return !success;
    }

    /** Success value, or null when {@link #isError()}. */
    public T getValue() {
        return value;
    }

    /** Human-safe error message, or null on success. Never contains secrets. */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** Machine-readable error code (see class Javadoc), or null on success. */
    public String getErrorCode() {
        return errorCode;
    }

    /** HTTP status of the failed call, or 0 when the failure happened before any HTTP response. */
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String toString() {
        if (success) {
            return "Result{success, value=" + value + "}";
        }
        return "Result{error, code=" + errorCode + ", http=" + httpStatus + ", message=" + errorMessage + "}";
    }
}
