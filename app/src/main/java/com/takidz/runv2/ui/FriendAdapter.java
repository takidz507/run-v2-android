package com.takidz.runv2.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.takidz.runv2.R;
import com.takidz.runv2.model.Friend;

import java.util.List;

/** RecyclerView adapter for search results / friends list. Display name big, @username small below. */
public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    public interface OnAddFriendListener {
        void onAddFriend(Friend friend);
    }

    private final List<Friend> friends;
    private final OnAddFriendListener listener;
    private final boolean showAddButton;

    public FriendAdapter(List<Friend> friends, OnAddFriendListener listener, boolean showAddButton) {
        this.friends = friends;
        this.listener = listener;
        this.showAddButton = showAddButton;
    }

    public void updateFriends(List<Friend> newFriends) {
        friends.clear();
        friends.addAll(newFriends);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        Friend friend = friends.get(position);
        holder.displayName.setText(friend.getDisplayName());
        holder.username.setText(friend.getUsername());
        holder.onlineDot.setVisibility(friend.isOnline() ? View.VISIBLE : View.INVISIBLE);
        holder.addButton.setVisibility(showAddButton ? View.VISIBLE : View.GONE);
        holder.addButton.setOnClickListener(v -> {
            if (listener != null) listener.onAddFriend(friend);
        });
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        final View onlineDot;
        final TextView displayName;
        final TextView username;
        final Button addButton;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            onlineDot = itemView.findViewById(R.id.view_online_dot);
            displayName = itemView.findViewById(R.id.text_friend_display_name);
            username = itemView.findViewById(R.id.text_friend_username);
            addButton = itemView.findViewById(R.id.button_add_friend);
        }
    }
}
