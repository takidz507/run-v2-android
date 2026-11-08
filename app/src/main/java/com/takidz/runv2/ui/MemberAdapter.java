package com.takidz.runv2.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.takidz.runv2.R;
import com.takidz.runv2.model.Member;

import java.util.List;

/** RecyclerView adapter for the room member list. */
public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    private final List<Member> members;

    public MemberAdapter(List<Member> members) {
        this.members = members;
    }

    public void updateMembers(List<Member> newMembers) {
        members.clear();
        members.addAll(newMembers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Member member = members.get(position);
        holder.displayName.setText(member.getDisplayName());
        holder.username.setText(member.getUsername());
        holder.onlineDot.setVisibility(member.isOnline() ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        final View onlineDot;
        final TextView displayName;
        final TextView username;

        MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            onlineDot = itemView.findViewById(R.id.view_member_online_dot);
            displayName = itemView.findViewById(R.id.text_member_display_name);
            username = itemView.findViewById(R.id.text_member_username);
        }
    }
}
