package com.takidz.runv2.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takidz.runv2.R;

/**
 * 1-to-1 video call screen: timer, mute / camera / speaker / end.
 *
 * TODO(WebRTC — out of scope for this worker): this screen is UI ONLY.
 * No WebRTC PeerConnection, no ICE, no signalling is wired here.
 * The call layer must add: remote/local video SurfaceViews, a PeerConnection
 * using {@link com.takidz.runv2.model.TurnCredentials} from the Matrix
 * client worker, and real mute/camera/speaker control. Do not present
 * this screen as a working call until that wiring lands.
 */
public class CallActivity extends AppCompatActivity {

    public static final String EXTRA_PEER_NAME = "peer_name";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView textTimer;
    private int elapsedSeconds;
    private boolean muted;
    private boolean cameraOff;
    private boolean speakerOn;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            elapsedSeconds++;
            textTimer.setText(formatTime(elapsedSeconds));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        String peer = getIntent().getStringExtra(EXTRA_PEER_NAME);
        TextView textPeer = findViewById(R.id.text_call_peer);
        textPeer.setText(peer == null || peer.isEmpty() ? getString(R.string.call_title) : peer);
        textTimer = findViewById(R.id.text_call_timer);
        textTimer.setText(formatTime(0));

        Button buttonMute = findViewById(R.id.button_mute);
        Button buttonCamera = findViewById(R.id.button_camera);
        Button buttonSpeaker = findViewById(R.id.button_speaker);
        Button buttonEnd = findViewById(R.id.button_end_call);

        // TODO(WebRTC): these toggles only flip local UI state today.
        buttonMute.setOnClickListener(v -> {
            muted = !muted;
            buttonMute.setAlpha(muted ? 0.5f : 1.0f);
        });
        buttonCamera.setOnClickListener(v -> {
            cameraOff = !cameraOff;
            buttonCamera.setAlpha(cameraOff ? 0.5f : 1.0f);
        });
        buttonSpeaker.setOnClickListener(v -> {
            speakerOn = !speakerOn;
            buttonSpeaker.setAlpha(speakerOn ? 0.5f : 1.0f);
        });
        buttonEnd.setOnClickListener(v -> finish());

        handler.postDelayed(tick, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
