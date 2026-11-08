package com.takidz.runv2.model;

/**
 * One chat message (a Matrix {@code m.room.message} event, or any room event
 * carrying a readable body).
 *
 * <p>Field shapes (exact):
 * <ul>
 *   <li>{@code eventId} — event ID, e.g. {@code "$abc123:tchncs.de"} (never null).</li>
 *   <li>{@code roomId} — room the event belongs to (never null).</li>
 *   <li>{@code sender} — full Matrix user ID of the sender, e.g. {@code "@taki:tchncs.de"}.</li>
 *   <li>{@code body} — message text (for {@code m.text}); may be empty, never null.</li>
 *   <li>{@code timestamp} — {@code origin_server_ts}, epoch milliseconds.</li>
 *   <li>{@code type} — Matrix event type, normally {@code "m.room.message"}.</li>
 *   <li>{@code msgType} — {@code msgtype} inside content, e.g. {@code "m.text"},
 *       {@code "m.image"}; may be null for non-message events.</li>
 * </ul>
 *
 * <p>End-to-end encrypted events ({@code m.room.encrypted}) arrive with no
 * readable body in this layer — see {@code PinBackup} / {@code MATRIX_CLIENT.md}:
 * real Olm/Megolm decryption is NOT implemented here.
 */
public final class Message {

    private final String eventId;
    private final String roomId;
    private final String sender;
    private final String body;
    private final long timestamp;
    private final String type;
    private final String msgType;

    public Message(String eventId, String roomId, String sender, String body,
                   long timestamp, String type, String msgType) {
        if (eventId == null || eventId.isEmpty()) {
            throw new IllegalArgumentException("eventId must not be null or empty");
        }
        if (roomId == null || roomId.isEmpty()) {
            throw new IllegalArgumentException("roomId must not be null or empty");
        }
        this.eventId = eventId;
        this.roomId = roomId;
        this.sender = sender;
        this.body = body == null ? "" : body;
        this.timestamp = timestamp;
        this.type = type;
        this.msgType = msgType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getMsgType() {
        return msgType;
    }

    @Override
    public String toString() {
        return "Message{eventId=" + eventId + ", sender=" + sender + ", ts=" + timestamp + "}";
    }
}
