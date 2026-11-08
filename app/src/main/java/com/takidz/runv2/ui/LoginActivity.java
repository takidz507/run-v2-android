package com.takidz.runv2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.takidz.runv2.R;
import com.takidz.runv2.security.SecureStore;

/**
 * Login screen. Username + password only — no email, no phone.
 * The password is typed by the user in-app and is never logged,
 * printed, or stored in plain text (see {@link SecureStore}).
 */
public class LoginActivity extends AppCompatActivity {

    private SecureStore secureStore;
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private TextView textError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        secureStore = new SecureStore(this);

        // Auto-login after first login.
        if (secureStore.hasCredentials()) {
            startActivity(new Intent(this, RoomsActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        textError = findViewById(R.id.text_login_error);
        Button buttonLogin = findViewById(R.id.button_login);
        buttonLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = editUsername.getText() == null ? "" : editUsername.getText().toString().trim();
        String password = editPassword.getText() == null ? "" : editPassword.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_empty));
            return;
        }
        if (!username.startsWith("@")) {
            username = "@" + username;
        }
        // TODO(Matrix worker): replace this local save with a real login call
        // via MatrixClient.login() and save credentials only on success.
        secureStore.saveCredentials(username, password);
        // Clear the password field immediately; never keep it on screen.
        editPassword.setText("");
        Toast.makeText(this, getString(R.string.login_button), Toast.LENGTH_SHORT).show();
        if (!secureStore.hasPin()) {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CREATE);
            startActivity(intent);
        } else {
            startActivity(new Intent(this, RoomsActivity.class));
        }
        finish();
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }
}
