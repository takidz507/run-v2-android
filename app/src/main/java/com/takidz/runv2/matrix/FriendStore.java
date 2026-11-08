package com.takidz.runv2.matrix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Result;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The user's friends list: in-memory map + persistence through a
 * {@link SecureStore} (the app's encrypted store in production — see
 * {@link AndroidSecureStore} — or {@link InMemorySecureStore} in tests).
 *
 * <p>What is stored: user ID, display name, avatar URL, presence. That is all.
 * NO passwords, NO PINs, NO access tokens are ever written here.
 * Adding a friend is local only — it sends nothing to the server and creates
 * no Matrix contact; presence is fetched on demand from the server.
 *
 * <p>Persistence format (key {@code runv2.friends.v1}): a JSON array of Friend
 * objects, written on every mutation. A corrupt stored value is discarded
 * (the list starts empty) rather than crashing.
 *
 * <p>Threading: methods are synchronized; {@link #refreshPresence(MatrixHttpClient)}
 * performs network I/O and must run off the UI thread.
 */
public final class FriendStore {

    static final String STORE_KEY = "runv2.friends.v1";

    private static final Type FRIEND_LIST_TYPE = new TypeToken<List<Friend>>() {}.getType();

    private final SecureStore store;
    private final Gson gson;
    private final Map<String, Friend> friends = new LinkedHashMap<>();

    public FriendStore(SecureStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
        this.gson = new GsonBuilder().create();
        load();
    }

    /** Adds or replaces a friend, then persists. */
    public synchronized Result<Void> addFriend(Friend friend) {
        if (friend == null || friend.getUserId() == null || friend.getUserId().trim().isEmpty()) {
            return Result.error("INVALID_ARGUMENT", "Friend needs a user ID.");
        }
        friends.put(friend.getUserId(), friend);
        persist();
        return Result.ok(null);
    }

    /** Removes a friend by user ID, then persists. */
    public synchronized Result<Void> removeFriend(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Result.error("INVALID_ARGUMENT", "User ID is required.");
        }
        if (friends.remove(userId) == null) {
            return Result.error("NOT_FOUND", "That user is not in the friends list.");
        }
        persist();
        return Result.ok(null);
    }

    /** Returns the friend with this user ID, or null when absent. */
    public synchronized Friend getFriend(String userId) {
        return friends.get(userId);
    }

    /** All friends, sorted by display label (case-insensitive). */
    public synchronized List<Friend> getAllFriends() {
        List<Friend> out = new ArrayList<>(friends.values());
        Collections.sort(out, new Comparator<Friend>() {
            @Override
            public int compare(Friend a, Friend b) {
                return a.getDisplayLabel().compareToIgnoreCase(b.getDisplayLabel());
            }
        });
        return out;
    }

    /** Number of friends stored. */
    public synchronized int size() {
        return friends.size();
    }

    /**
     * Refreshes every friend's presence from the server
     * ({@code GET /presence/{userId}/status} per friend) and persists.
     * A single friend's failure is skipped, not fatal.
     *
     * @return the number of friends whose presence was updated, or an error
     *         when the user is not logged in / offline (nothing was updated).
     */
    public Result<Integer> refreshPresence(MatrixHttpClient client) {
        if (client == null) {
            return Result.error("INVALID_ARGUMENT", "Client is required.");
        }
        if (!client.isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before refreshing presence.");
        }
        List<Friend> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(friends.values());
        }
        int updated = 0;
        for (Friend friend : snapshot) {
            Result<MatrixHttpClient.PresenceStatus> r = client.getPresence(friend.getUserId());
            if (r.isError()) {
                continue;
            }
            MatrixHttpClient.PresenceStatus status = r.getValue();
            Friend refreshed = friend.withPresence(
                    status.toFriendPresence(), status.getLastActiveAgoMs(), status.getStatusMessage());
            synchronized (this) {
                friends.put(refreshed.getUserId(), refreshed);
            }
            updated++;
        }
        persist();
        return Result.ok(updated);
    }

    private void persist() {
        List<Friend> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(friends.values());
        }
        store.putString(STORE_KEY, gson.toJson(snapshot));
    }

    private void load() {
        String json = store.getString(STORE_KEY);
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            List<Friend> loaded = gson.fromJson(json, FRIEND_LIST_TYPE);
            if (loaded != null) {
                for (Friend f : loaded) {
                    if (f != null && f.getUserId() != null && !f.getUserId().isEmpty()) {
                        friends.put(f.getUserId(), f);
                    }
                }
            }
        } catch (JsonSyntaxException ignored) {
            // Corrupt stored value: start empty rather than crashing.
            friends.clear();
        }
    }
}
