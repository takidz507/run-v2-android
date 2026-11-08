package com.takidz.runv2.model;

/**
 * A chat room.
 *
 * <p>Two constructors exist by design (see LocalSelfTest#testModels):
 * <ul>
 *   <li>The 8-arg Matrix constructor: {@code (roomId, name, canonicalAlias, topic,
 *       avatarUrl, numJoinedMembers, lastMessage, lastTimestamp)} — used by
 *       {@code MatrixHttpClient} / {@code RoomDirectory}. Names may be null;
 *       UI code must use {@link #getDisplayName()}, never {@link #getName()}.</li>
 *   <li>The 6-arg legacy constructor: {@code (id, name, lastMessage, memberCount,
 *       inviteCode, group)} — used by the scaffold placeholder data and the
 *       Run invite-link flow ({@code run://join/<code>}).</li>
 * </ul>
 */
public class Room {
    private final String roomId;
    private final String name;
    private final String canonicalAlias;
    private final String topic;
    private final String avatarUrl;
    private final int numJoinedMembers;
    private final String lastMessage;
    private final long lastTimestamp;
    private final String inviteCode;
    private final boolean group;

    /** Matrix constructor (see class Javadoc). */
    public Room(String roomId, String name, String canonicalAlias, String topic,
                String avatarUrl, int numJoinedMembers, String lastMessage, long lastTimestamp) {
        this.roomId = roomId;
        this.name = name;
        this.canonicalAlias = canonicalAlias;
        this.topic = topic;
        this.avatarUrl = avatarUrl;
        this.numJoinedMembers = numJoinedMembers;
        this.lastMessage = lastMessage;
        this.lastTimestamp = lastTimestamp;
        this.inviteCode = null;
        this.group = true;
    }

    /** Legacy scaffold constructor (see class Javadoc). */
    public Room(String id, String name, String lastMessage, int memberCount, String inviteCode, boolean group) {
        this.roomId = id;
        this.name = name;
        this.canonicalAlias = null;
        this.topic = null;
        this.avatarUrl = null;
        this.numJoinedMembers = memberCount;
        this.lastMessage = lastMessage;
        this.lastTimestamp = 0L;
        this.inviteCode = inviteCode;
        this.group = group;
    }

    public String getId() { return roomId; }
    public String getRoomId() { return roomId; }
    public String getName() { return name; }

    /** Display name: room name, else canonical alias, else room ID. Never null when roomId is set. */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) return name;
        if (canonicalAlias != null && !canonicalAlias.isEmpty()) return canonicalAlias;
        return roomId;
    }

    public String getCanonicalAlias() { return canonicalAlias; }
    public String getTopic() { return topic; }
    public String getAvatarUrl() { return avatarUrl; }
    public int getNumJoinedMembers() { return numJoinedMembers; }
    public int getMemberCount() { return numJoinedMembers; }
    public String getLastMessage() { return lastMessage; }
    public long getLastTimestamp() { return lastTimestamp; }
    public String getInviteCode() { return inviteCode; }
    public boolean isGroup() { return group; }

    /**
     * App-specific invite link, e.g. {@code run://join/RUN-AB12}.
     * Null when this room has no invite code (Matrix rooms without one).
     */
    public String getInviteLink() {
        return inviteCode == null ? null : "run://join/" + inviteCode;
    }
}
