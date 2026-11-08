package com.takidz.runv2.data;

import com.takidz.runv2.model.ChatMessage;
import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Placeholder data layer for the scaffold.
 * All data below is local sample data so every screen can be exercised
 * without a network connection. The Matrix client worker replaces these
 * methods with real calls; no network call is invented here.
 */
public final class PlaceholderData {

    private PlaceholderData() {}

    private static final List<Room> ROOMS = new ArrayList<>();
    private static final List<Friend> FRIENDS = new ArrayList<>();

    static {
        ROOMS.add(new Room("room-1", "Section D", "See you at 08:00 tomorrow", 42, "RUN-DSEC", true));
        ROOMS.add(new Room("room-2", "Study group", "I shared the chemistry notes", 6, "RUN-STUD", true));
        ROOMS.add(new Room("room-3", "Amine", "Sounds good!", 2, "RUN-AMIN", false));
        FRIENDS.add(new Friend("@taki", "Taki", true));
        FRIENDS.add(new Friend("@amine", "Amine Benali", true));
        FRIENDS.add(new Friend("@sara", "Sara Haddad", false));
        FRIENDS.add(new Friend("@yassin", "Yassin Merabet", true));
    }

    public static List<Room> getRooms() {
        return new ArrayList<>(ROOMS);
    }

    public static List<Friend> getFriends() {
        return new ArrayList<>(FRIENDS);
    }

    public static List<ChatMessage> getMessagesForRoom(String roomId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("m1", "Amine Benali", "Hello everyone", "09:41", false));
        messages.add(new ChatMessage("m2", "Sara Haddad", "Did you see the new timetable?", "09:42", false));
        messages.add(new ChatMessage("m3", "You", "Yes, Structure of Computers moved to 14:30", "09:45", true));
        return messages;
    }

    /** Friends searchable by @username AND display name (case-insensitive). */
    public static List<Friend> searchFriends(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<Friend> out = new ArrayList<>();
        for (Friend f : FRIENDS) {
            if (q.isEmpty()
                    || f.getUsername().toLowerCase(Locale.ROOT).contains(q)
                    || f.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Rooms searchable by name AND invite code (case-insensitive). */
    public static List<Room> searchRooms(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<Room> out = new ArrayList<>();
        for (Room r : ROOMS) {
            if (q.isEmpty()
                    || r.getName().toLowerCase(Locale.ROOT).contains(q)
                    || r.getInviteCode().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(r);
            }
        }
        return out;
    }

    public static Room getRoomById(String roomId) {
        for (Room r : ROOMS) {
            if (r.getId().equals(roomId)) return r;
        }
        return ROOMS.isEmpty() ? null : ROOMS.get(0);
    }

    public static Room createRoom(String name, String description, List<Friend> members) {
        String code = "RUN-" + name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (code.length() > 9) code = code.substring(0, 9);
        Room room = new Room("room-" + (ROOMS.size() + 1), name, "", members.size() + 1, code, true);
        ROOMS.add(room);
        return room;
    }
}
