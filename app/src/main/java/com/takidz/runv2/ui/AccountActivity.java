package com.takidz.runv2.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.takidz.runv2.R;
import com.takidz.runv2.matrix.MatrixConfig;
import com.takidz.runv2.security.SecureStore;

/**
 * Account screen: shows only the @username and "Run account".
 * No homeserver, Matrix, server or protocol wording appears anywhere here.
 * Call settings: 480p default (see {@link MatrixConfig#DEFAULT_VIDEO_QUALITY}),
 * data saver, audio-only.
 */
public class AccountActivity extends AppCompatActivity {

    private static final String CALL_PREFS = "run_call_settings";
    private static final String KEY_DATA_SAVER = "data_saver";
    private static final String KEY_AUDIO_ONLY = "audio_only";

    private SecureStore secureStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        secureStore = new SecureStore(this);

        TextView textUsername = findViewById(R.id.text_account_username);
        String username = secureStore.getUsername();
        textUsername.setText(username == null ? "" : username);

        findViewById(R.id.button_encryption).setOnClickListener(v ->
                Toast.makeText(this, R.string.account_encryption_sub, Toast.LENGTH_LONG).show());
        findViewById(R.id.button_login_other_phone).setOnClickListener(v -> {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_RESTORE);
            startActivity(intent);
        });
        findViewById(R.id.button_logout).setOnClickListener(v -> {
            secureStore.clearAll();
            Toast.makeText(this, R.string.logout_confirm, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        SharedPreferences callPrefs = getSharedPreferences(CALL_PREFS, MODE_PRIVATE);
        SwitchMaterial switchDataSaver = findViewById(R.id.switch_data_saver);
        SwitchMaterial switchAudioOnly = findViewById(R.id.switch_audio_only);
        switchDataSaver.setChecked(callPrefs.getBoolean(KEY_DATA_SAVER, false));
        switchAudioOnly.setChecked(callPrefs.getBoolean(KEY_AUDIO_ONLY, false));
        switchDataSaver.setOnCheckedChangeListener((button, checked) ->
                callPrefs.edit().putBoolean(KEY_DATA_SAVER, checked).apply());
        switchAudioOnly.setOnCheckedChangeListener((button, checked) ->
                callPrefs.edit().putBoolean(KEY_AUDIO_ONLY, checked).apply());
    }
}
