package com.takidz.runv2.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.takidz.runv2.R;
import com.takidz.runv2.data.PlaceholderData;
import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Member;
import com.takidz.runv2.model.Room;

import java.util.ArrayList;
import java.util.List;

/**
 * Room detail: app-specific invite link (run://join/&lt;code&gt;),
 * permanent code RUN-xxxx, Copy buttons, Share, member list.
 * Also handles incoming run://join/&lt;code&gt; deep links.
 */
public class RoomDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_ID = "room_id";

    private Room room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_detail);
        room = resolveRoom();
        if (room == null) {
            finish();
            return;
        }

        TextView textName = findViewById(R.id.text_room_name);
        TextView textMembers = findViewById(R.id.text_room_members);
        TextView textLink = findViewById(R.id.text_invite_link);
        TextView textCode = findViewById(R.id.text_invite_code);
        textName.setText(room.getDisplayName());
        textMembers.setText(getString(R.string.members_count, room.getMemberCount()));
        textLink.setText(room.getInviteLink());
        textCode.setText(room.getInviteCode());

        Button copyLink = findViewById(R.id.button_copy_link);
        Button copyCode = findViewById(R.id.button_copy_code);
        Button share = findViewById(R.id.button_share);
        copyLink.setOnClickListener(v -> copyToClipboard("invite_link", room.getInviteLink()));
        copyCode.setOnClickListener(v -> copyToClipboard("invite_code", room.getInviteCode()));
        share.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, room.getInviteLink());
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        });

        RecyclerView recycler = findViewById(R.id.recycler_members);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        List<Member> members = new ArrayList<>();
        for (Friend f : PlaceholderData.getFriends()) {
            members.add(Member.fromFriend(f));
        }
        recycler.setAdapter(new MemberAdapter(members));
    }

    /** Resolves the room from the intent extra, or from a run://join/<code> deep link. */
    private Room resolveRoom() {
        String roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        if (roomId != null) {
            return PlaceholderData.getRoomById(roomId);
        }
        Uri data = getIntent().getData();
        if (data != null && "run".equals(data.getScheme()) && "join".equals(data.getHost())) {
            String code = data.getLastPathSegment();
            if (code != null) {
                List<Room> found = PlaceholderData.searchRooms(code);
                if (!found.isEmpty()) return found.get(0);
            }
        }
        return null;
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }
}
