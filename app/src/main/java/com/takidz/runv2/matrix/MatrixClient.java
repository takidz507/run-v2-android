package com.takidz.runv2.matrix;

/**
 * Interface for the real Matrix client. Owned by the Matrix client worker.
 * This scaffold ships no implementation and makes NO network calls here;
 * screens use {@link com.takidz.runv2.data.PlaceholderData} instead.
 */
public interface MatrixClient {

    interface AuthCallback {
        void onSuccess(String username);
        void onError(String message);
    }

    /** TODO(Matrix worker): log in with username + password. */
    void login(String username, String password, AuthCallback callback);

    /** TODO(Matrix worker): log out and clear the session. */
    void logout();

    /** TODO(Matrix worker): true when a session is active. */
    boolean isLoggedIn();

    /** TODO(Matrix worker): the logged-in @username, or null. */
    String getCurrentUsername();
}
