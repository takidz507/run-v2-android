package com.takidz.runv2.matrix;

/**
 * Internal connection constants for the Matrix client layer.
 * NOTE: these values must NEVER be shown in any UI string.
 * The UI shows only the @username and "Run account".
 *
 * <p>Values pinned by the Matrix layer's LocalSelfTest: {@link #BASE_URL} and
 * {@link #CLIENT_API_PATH}. The remaining values (timeouts, device IDs,
 * backup account-data type) are scaffold choices — the Matrix client worker
 * owns them and may change them.
 */
public final class MatrixConfig {
    private MatrixConfig() {}

    /** Matrix API base URL (https://matrix.tchncs.de — the API host for tchncs.de). */
    public static final String BASE_URL = "https://matrix.tchncs.de";

    /** Human homeserver name; same host as {@link #BASE_URL}. Internal only — never display. */
    public static final String HOMESERVER_URL = BASE_URL;

    /** Matrix user-ID server name (the part after the colon in @user:tchncs.de). */
    public static final String SERVER_NAME = "tchncs.de";

    /** Client-Server API path. */
    public static final String CLIENT_API_PATH = "/_matrix/client/v3";

    /** HTTP User-Agent sent with API calls. */
    public static final String USER_AGENT = "Run-v2/1.0.0 (Android)";

    /** Device ID sent at login. */
    public static final String DEVICE_ID = "RUNV2ANDROID";

    /** Human-readable device name sent at login. */
    public static final String DEVICE_DISPLAY_NAME = "Run v2 Android";

    /** Connect timeout, seconds. */
    public static final int CONNECT_TIMEOUT_SECONDS = 10;
    /** Read/write timeout for normal API calls, seconds. */
    public static final int READ_TIMEOUT_SECONDS = 30;
    /** Read timeout for long-poll /sync, seconds. */
    public static final int SYNC_READ_TIMEOUT_SECONDS = 65;
    /** Long-poll timeout sent to /sync, milliseconds. */
    public static final int SYNC_TIMEOUT_MS = 30000;

    /** Default page size for user/room search. */
    public static final int DEFAULT_SEARCH_LIMIT = 20;

    /** Account-data type under which the PIN-encrypted key backup blob is stored. */
    public static final String KEY_BACKUP_ACCOUNT_DATA_TYPE = "run.v2.key_backup";

    /** Default 1-to-1 video quality. */
    public static final String DEFAULT_VIDEO_QUALITY = "480p";
}
