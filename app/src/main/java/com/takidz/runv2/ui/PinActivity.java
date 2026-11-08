package com.takidz.runv2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takidz.runv2.R;
import com.takidz.runv2.security.SecureStore;

/**
 * 6-digit PIN pad. The PIN is NOT the account password; it protects the
 * encrypted E2E key backup. Only a SHA-256 hash is stored (see SecureStore).
 * Modes: create (enter + confirm) and restore (verify on a new phone).
 */
public class PinActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_CREATE = "create";
    public static final String MODE_RESTORE = "restore";

    private static final int PIN_LENGTH = 6;

    private SecureStore secureStore;
    private String mode;
    private final StringBuilder pin = new StringBuilder();
    private String firstPin;
    private TextView[] dots = new TextView[PIN_LENGTH];
    private TextView textTitle;
    private TextView textError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        secureStore = new SecureStore(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_CREATE;

        textTitle = findViewById(R.id.text_pin_title);
        textError = findViewById(R.id.text_pin_error);
        int[] dotIds = {R.id.dot_0, R.id.dot_1, R.id.dot_2, R.id.dot_3, R.id.dot_4, R.id.dot_5};
        for (int i = 0; i < PIN_LENGTH; i++) {
            dots[i] = findViewById(dotIds[i]);
        }
        if (MODE_RESTORE.equals(mode)) {
            textTitle.setText(R.string.pin_restore_title);
        } else {
            textTitle.setText(R.string.pin_create_title);
        }

        int[] keyIds = {R.id.pin_key_0, R.id.pin_key_1, R.id.pin_key_2, R.id.pin_key_3,
                R.id.pin_key_4, R.id.pin_key_5, R.id.pin_key_6, R.id.pin_key_7,
                R.id.pin_key_8, R.id.pin_key_9};
        for (int digit = 0; digit <= 9; digit++) {
            final String d = String.valueOf(digit);
            Button key = findViewById(keyIds[digit]);
            key.setOnClickListener(v -> onDigit(d));
        }
        findViewById(R.id.pin_key_delete).setOnClickListener(v -> onDelete());
    }

    private void onDigit(String digit) {
        if (pin.length() >= PIN_LENGTH) return;
        pin.append(digit);
        updateDots();
        if (pin.length() == PIN_LENGTH) {
            onPinComplete();
        }
    }

    private void onDelete() {
        if (pin.length() > 0) {
            pin.deleteCharAt(pin.length() - 1);
            updateDots();
        }
    }

    private void updateDots() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            dots[i].setText(i < pin.length() ? "●" : "○");
        }
    }

    private void onPinComplete() {
        String entered = pin.toString();
        if (MODE_RESTORE.equals(mode)) {
            if (secureStore.verifyPin(entered)) {
                goToRooms();
            } else {
                showError(getString(R.string.pin_wrong));
                resetPin();
            }
            return;
        }
        // Create mode: first entry, then confirm.
        if (firstPin == null) {
            firstPin = entered;
            resetPin();
            textTitle.setText(R.string.pin_confirm_title);
        } else if (firstPin.equals(entered)) {
            secureStore.setPin(entered);
            firstPin = null;
            goToRooms();
        } else {
            showError(getString(R.string.pin_mismatch));
            firstPin = null;
            resetPin();
            textTitle.setText(R.string.pin_create_title);
        }
    }

    private void resetPin() {
        pin.setLength(0);
        updateDots();
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void goToRooms() {
        startActivity(new Intent(this, RoomsActivity.class));
        finish();
    }
}
