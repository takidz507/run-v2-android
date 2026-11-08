package com.takidz.runv2.matrix;

/**
 * Placeholder implementation of {@link MatrixClient}.
 * Every method is an explicit TODO for the Matrix client worker.
 * It performs no network I/O and must not be mistaken for a working client.
 */
public class MatrixClientStub implements MatrixClient {

    @Override
    public void login(String username, String password, AuthCallback callback) {
        // TODO(Matrix worker): implement real login. Do not log the password.
        callback.onError("Not implemented yet");
    }

    @Override
    public void logout() {
        // TODO(Matrix worker): implement real logout.
    }

    @Override
    public boolean isLoggedIn() {
        // TODO(Matrix worker): return real session state.
        return false;
    }

    @Override
    public String getCurrentUsername() {
        // TODO(Matrix worker): return the logged-in @username.
        return null;
    }
}
