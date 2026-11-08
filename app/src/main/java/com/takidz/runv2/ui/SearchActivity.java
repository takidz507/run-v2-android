package com.takidz.runv2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.takidz.runv2.R;
import com.takidz.runv2.data.PlaceholderData;
import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Room;

import java.util.ArrayList;

/**
 * Search screen with Friends / Rooms tabs.
 * Friends: searchable by @username AND display name.
 * Rooms: searchable by name AND code.
 */
public class SearchActivity extends AppCompatActivity {

    private boolean showingFriends = true;
    private FriendAdapter friendAdapter;
    private RoomAdapter roomAdapter;
    private RecyclerView recycler;
    private TextView textEmpty;
    private TextInputEditText editQuery;
    private Button buttonTabFriends;
    private Button buttonTabRooms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        recycler = findViewById(R.id.recycler_search);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        textEmpty = findViewById(R.id.text_search_empty);
        editQuery = findViewById(R.id.edit_search_query);
        buttonTabFriends = findViewById(R.id.button_tab_friends);
        buttonTabRooms = findViewById(R.id.button_tab_rooms);

        friendAdapter = new FriendAdapter(new ArrayList<>(), friend -> {
            // TODO(Matrix worker): send a real friend request.
            Toast.makeText(this, R.string.friend_added, Toast.LENGTH_SHORT).show();
        }, true);
        roomAdapter = new RoomAdapter(new ArrayList<>(), room -> {
            Intent intent = new Intent(this, RoomDetailActivity.class);
            intent.putExtra(RoomDetailActivity.EXTRA_ROOM_ID, room.getId());
            startActivity(intent);
        });

        buttonTabFriends.setOnClickListener(v -> showFriendsTab());
        buttonTabRooms.setOnClickListener(v -> showRoomsTab());
        editQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                runSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        showFriendsTab();
    }

    private void showFriendsTab() {
        showingFriends = true;
        editQuery.setHint(R.string.search_friends_hint);
        recycler.setAdapter(friendAdapter);
        runSearch();
    }

    private void showRoomsTab() {
        showingFriends = false;
        editQuery.setHint(R.string.search_rooms_hint);
        recycler.setAdapter(roomAdapter);
        runSearch();
    }

    private void runSearch() {
        String query = editQuery.getText() == null ? "" : editQuery.getText().toString();
        if (showingFriends) {
            friendAdapter.updateFriends(PlaceholderData.searchFriends(query));
            textEmpty.setVisibility(friendAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        } else {
            roomAdapter.updateRooms(PlaceholderData.searchRooms(query));
            textEmpty.setVisibility(roomAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }
}
