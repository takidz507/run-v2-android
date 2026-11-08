package com.takidz.runv2.model;

/** A single chat message. Placeholder data layer. */
public class ChatMessage {
    private final String id;
    private final String sender;
    private final String body;
    private final String timestamp;
    private final boolean own;

    public ChatMessage(String id, String sender, String body, String timestamp, boolean own) {
        this.id = id;
        this.sender = sender;
        this.body = body;
        this.timestamp = timestamp;
        this.own = own;
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getBody() { return body; }
    public String getTimestamp() { return timestamp; }
    public boolean isOwn() { return own; }
}
