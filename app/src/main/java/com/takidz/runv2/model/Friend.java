package com.takidz.runv2.model;

/**
 * A friend / contact (a Matrix user).
 *
 * <p>Constructors (see LocalSelfTest#testModels):
 * <ul>
 *   <li>{@code (userId, displayName, avatarUrl)} — used by {@code MatrixHttpClient}
 *       search/profile results; presence starts {@link Presence#UNKNOWN}.</li>
 *   <li>{@code (userId, displayName, avatarUrl, presence, lastActiveAgoMs, statusMessage)}
 *       — full form, used by {@code FriendStore} presence refresh.</li>
 *   <li>{@code (userId, displayName, online)} — legacy scaffold form; {@code online}
 *       maps to {@link Presence#ONLINE} / {@link Presence#OFFLINE}.</li>
 * </ul>
 * Searchable by @username ({@link #getUserId()}) AND display name.
 */
public class Friend {

    public enum Presence {
        UNKNOWN,
        ONLINE,
        OFFLINE,
        UNAVAILABLE
    }

    private final String userId;
    private final String displayName;
    private final String avatarUrl;
    private final Presence presence;
    private final long lastActiveAgoMs;
    private final String statusMessage;

    public Friend(String userId, String displayName, String avatarUrl) {
        this(userId, displayName, avatarUrl, Presence.UNKNOWN, 0L, null);
    }

    public Friend(String userId, String displayName, String avatarUrl,
                  Presence presence, long lastActiveAgoMs, String statusMessage) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.presence = presence == null ? Presence.UNKNOWN : presence;
        this.lastActiveAgoMs = lastActiveAgoMs;
        this.statusMessage = statusMessage;
    }

    /** Legacy scaffold constructor: online flag maps to ONLINE / OFFLINE presence. */
    public Friend(String userId, String displayName, boolean online) {
        this(userId, displayName, null, online ? Presence.ONLINE : Presence.OFFLINE, 0L, null);
    }

    public String getUserId() { return userId; }
    /** Alias for {@link #getUserId()} — the @username shown in the UI. */
    public String getUsername() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public Presence getPresence() { return presence; }
    public long getLastActiveAgoMs() { return lastActiveAgoMs; }
    public String getStatusMessage() { return statusMessage; }

    /** Label for lists/sorting: display name when present, else the user ID. */
    public String getDisplayLabel() {
        return displayName != null && !displayName.isEmpty() ? displayName : userId;
    }

    /** Online indicator: true only when presence is ONLINE. */
    public boolean isOnline() { return presence == Presence.ONLINE; }

    /** Returns a copy of this friend with updated presence. */
    public Friend withPresence(Presence presence, long lastActiveAgoMs, String statusMessage) {
        return new Friend(userId, displayName, avatarUrl, presence, lastActiveAgoMs, statusMessage);
    }
}
