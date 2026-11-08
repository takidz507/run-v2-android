package com.takidz.runv2.matrix;

import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Result;
import com.takidz.runv2.model.Room;

import java.util.List;

/**
 * Room search / discovery, built on {@link MatrixHttpClient}.
 *
 * <p>Thin wrapper — the HTTP work lives in the client; this class is the
 * single place screens go for "find a room / find a person":
 * <ul>
 *   <li>{@link #listJoinedRooms()} — rooms the user already joined.</li>
 *   <li>{@link #searchPublicRooms(String, int)} — public rooms, filtered by name
 *       ({@code POST /publicRooms} with {@code generic_search_term}).</li>
 *   <li>{@link #searchUsers(String, int)} — users by @username and display name
 *       ({@code POST /user_directory/search}).</li>
 * </ul>
 *
 * <p>All methods are synchronous (network I/O) and return {@link Result};
 * call them off the UI thread, like every other client call.
 */
public final class RoomDirectory {

    private final MatrixHttpClient client;

    public RoomDirectory(MatrixHttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        this.client = client;
    }

    /** Rooms the user already joined. See {@link MatrixHttpClient#getJoinedRooms()} for the shape caveat. */
    public Result<List<Room>> listJoinedRooms() {
        return client.getJoinedRooms();
    }

    /**
     * Searches public rooms by name. An empty/null query lists public rooms
     * without a filter (up to {@code limit}).
     */
    public Result<List<Room>> searchPublicRooms(String query, int limit) {
        Result<MatrixHttpClient.PublicRoomsPage> page = client.searchPublicRooms(query, limit);
        if (page.isError()) {
            return Result.error(page.getErrorCode(), page.getErrorMessage(), page.getHttpStatus());
        }
        return Result.ok(page.getValue().getRooms());
    }

    /** Searches users by @username and/or display name. */
    public Result<List<Friend>> searchUsers(String query, int limit) {
        return client.searchUsers(query, limit);
    }
}
