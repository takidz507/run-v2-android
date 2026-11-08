package com.takidz.runv2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.takidz.runv2.R;
import com.takidz.runv2.data.PlaceholderData;
import com.takidz.runv2.model.ChatMessage;
import com.takidz.runv2.model.Room;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen chat: message list, send box, video-call button in the header. */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_ID = "room_id";

    private MessageAdapter adapter;
    private EditText editMessage;
    private Room room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        String roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        room = PlaceholderData.getRoomById(roomId);
        TextView textRoomName = findViewById(R.id.text_chat_room_name);
        textRoomName.setText(room == null ? "" : room.getDisplayName());

        RecyclerView recycler = findViewById(R.id.recycler_messages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(PlaceholderData.getMessagesForRoom(roomId));
        recycler.setAdapter(adapter);
        recycler.scrollToPosition(adapter.getItemCount() - 1);

        editMessage = findViewById(R.id.edit_message);
        Button buttonSend = findViewById(R.id.button_send);
        buttonSend.setOnClickListener(v -> sendMessage());

        findViewById(R.id.button_video_call).setOnClickListener(v -> {
            Intent intent = new Intent(this, CallActivity.class);
            intent.putExtra(CallActivity.EXTRA_PEER_NAME, room == null ? "" : room.getName());
            startActivity(intent);
        });
    }

    private void sendMessage() {
        String body = editMessage.getText() == null ? "" : editMessage.getText().toString().trim();
        if (body.isEmpty()) return;
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        // TODO(Matrix worker): send for real via MatrixClient; placeholder appends locally.
        adapter.addMessage(new ChatMessage("local-" + System.currentTimeMillis(), "You", body, time, true));
        editMessage.setText("");
    }
}
