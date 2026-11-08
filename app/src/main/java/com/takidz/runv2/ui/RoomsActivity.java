package com.takidz.runv2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.takidz.runv2.R;
import com.takidz.runv2.data.PlaceholderData;
import com.takidz.runv2.model.Room;

/** Main screen: rooms list with search bar, bottom tabs, "+" to create a group room. */
public class RoomsActivity extends AppCompatActivity {

    private RoomAdapter adapter;
    private TextView textEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rooms);

        RecyclerView recycler = findViewById(R.id.recycler_rooms);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        textEmpty = findViewById(R.id.text_rooms_empty);
        adapter = new RoomAdapter(PlaceholderData.getRooms(), room -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_ROOM_ID, room.getId());
            startActivity(intent);
        });
        recycler.setAdapter(adapter);
        updateEmptyState();

        findViewById(R.id.edit_search_bar).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.fab_create_room).setOnClickListener(v ->
                startActivity(new Intent(this, CreateRoomActivity.class)));

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_rooms);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calls) {
                startActivity(new Intent(this, CallActivity.class));
                return true;
            } else if (id == R.id.nav_account) {
                startActivity(new Intent(this, AccountActivity.class));
                return true;
            }
            return id == R.id.nav_rooms;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.updateRooms(PlaceholderData.getRooms());
        updateEmptyState();
    }

    private void updateEmptyState() {
        textEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
