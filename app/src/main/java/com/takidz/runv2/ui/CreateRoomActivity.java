package com.takidz.runv2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.takidz.runv2.R;
import com.takidz.runv2.data.PlaceholderData;
import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Room;

import java.util.ArrayList;
import java.util.List;

/** Create a group room: name, optional description, add members by search. */
public class CreateRoomActivity extends AppCompatActivity {

    private TextInputEditText editName;
    private TextInputEditText editDescription;
    private TextView textError;
    private FriendAdapter searchAdapter;
    private final List<Friend> selectedMembers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        editName = findViewById(R.id.edit_room_name);
        editDescription = findViewById(R.id.edit_room_description);
        textError = findViewById(R.id.text_create_room_error);

        RecyclerView recycler = findViewById(R.id.recycler_member_search);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new FriendAdapter(new ArrayList<>(), friend -> {
            boolean already = false;
            for (Friend f : selectedMembers) {
                if (f.getUsername().equals(friend.getUsername())) already = true;
            }
            if (!already) selectedMembers.add(friend);
        }, true);
        recycler.setAdapter(searchAdapter);

        TextInputEditText editMemberSearch = findViewById(R.id.edit_member_search);
        editMemberSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s == null ? "" : s.toString();
                searchAdapter.updateFriends(PlaceholderData.searchFriends(q));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.button_create_room).setOnClickListener(v -> createRoom());
    }

    private void createRoom() {
        String name = editName.getText() == null ? "" : editName.getText().toString().trim();
        if (name.isEmpty()) {
            textError.setText(R.string.create_room_error_name);
            textError.setVisibility(View.VISIBLE);
            return;
        }
        String description = editDescription.getText() == null ? "" : editDescription.getText().toString().trim();
        // TODO(Matrix worker): create the room for real; description is kept for that call.
        Room room = PlaceholderData.createRoom(name, description, selectedMembers);
        Intent intent = new Intent(this, RoomDetailActivity.class);
        intent.putExtra(RoomDetailActivity.EXTRA_ROOM_ID, room.getId());
        startActivity(intent);
        finish();
    }
}
