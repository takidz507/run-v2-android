package com.takidz.runv2.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.takidz.runv2.R;
import com.takidz.runv2.model.Room;

import java.util.List;

/** RecyclerView adapter for the rooms list. */
public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    public interface OnRoomClickListener {
        void onRoomClick(Room room);
    }

    private final List<Room> rooms;
    private final OnRoomClickListener listener;

    public RoomAdapter(List<Room> rooms, OnRoomClickListener listener) {
        this.rooms = rooms;
        this.listener = listener;
    }

    public void updateRooms(List<Room> newRooms) {
        rooms.clear();
        rooms.addAll(newRooms);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        Room room = rooms.get(position);
        holder.name.setText(room.getDisplayName());
        holder.lastMessage.setText(room.getLastMessage());
        holder.memberCount.setText(holder.itemView.getContext().getString(R.string.members_count, room.getMemberCount()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRoomClick(room);
        });
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView lastMessage;
        final TextView memberCount;

        RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_room_name);
            lastMessage = itemView.findViewById(R.id.text_room_last_message);
            memberCount = itemView.findViewById(R.id.text_room_member_count);
        }
    }
}
