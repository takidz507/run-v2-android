package com.takidz.runv2.model;

/** A member of a room. */
public class Member {
    private final String username;
    private final String displayName;
    private final boolean online;

    public Member(String username, String displayName, boolean online) {
        this.username = username;
        this.displayName = displayName;
        this.online = online;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public boolean isOnline() { return online; }

    public static Member fromFriend(Friend friend) {
        return new Member(friend.getUsername(), friend.getDisplayName(), friend.isOnline());
    }
}
