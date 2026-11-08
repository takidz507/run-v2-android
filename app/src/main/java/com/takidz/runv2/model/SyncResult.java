package com.takidz.runv2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One {@code /sync} response, reduced to what the UI needs.
 *
 * <p>Field shapes (exact):
 * <ul>
 *   <li>{@code nextBatch} — the {@code next_batch} token; pass it as {@code since}
 *       on the next sync call. Never null on success (empty string if the server sent none).</li>
 *   <li>{@code roomUpdates} — one {@link RoomUpdate} per joined room that had activity
 *       in this sync response; rooms with no new events are absent.</li>
 * </ul>
 *
 * <p>{@link RoomUpdate} shapes:
 * <ul>
 *   <li>{@code roomId} — Matrix room ID.</li>
 *   <li>{@code messages} — timeline events of this sync, newest last, as {@link Message}s.
 *       Only {@code m.room.message} events with a readable {@code body} are included;
 *       encrypted events ({@code m.room.encrypted}) are skipped by this layer.</li>
 *   <li>{@code newLastMessage} / {@code newLastTimestamp} — body / timestamp of the
 *       newest message in {@code messages}; null / 0 when the list is empty.</li>
 * </ul>
 */
public final class SyncResult {

    /** Per-room slice of a sync response. */
    public static final class RoomUpdate {
        private final String roomId;
        private final List<Message> messages;

        public RoomUpdate(String roomId, List<Message> messages) {
            if (roomId == null || roomId.isEmpty()) {
                throw new IllegalArgumentException("roomId must not be null or empty");
            }
            this.roomId = roomId;
            this.messages = messages == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(messages));
        }

        public String getRoomId() {
            return roomId;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public String getNewLastMessage() {
            if (messages.isEmpty()) {
                return null;
            }
            return messages.get(messages.size() - 1).getBody();
        }

        public long getNewLastTimestamp() {
            if (messages.isEmpty()) {
                return 0L;
            }
            return messages.get(messages.size() - 1).getTimestamp();
        }
    }

    private final String nextBatch;
    private final List<RoomUpdate> roomUpdates;

    public SyncResult(String nextBatch, List<RoomUpdate> roomUpdates) {
        this.nextBatch = nextBatch == null ? "" : nextBatch;
        this.roomUpdates = roomUpdates == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(roomUpdates));
    }

    public String getNextBatch() {
        return nextBatch;
    }

    public List<RoomUpdate> getRoomUpdates() {
        return roomUpdates;
    }

    @Override
    public String toString() {
        return "SyncResult{nextBatch=" + nextBatch + ", rooms=" + roomUpdates.size() + "}";
    }
}
