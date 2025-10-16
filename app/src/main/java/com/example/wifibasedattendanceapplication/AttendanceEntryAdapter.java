package com.example.wifibasedattendanceapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AttendanceEntryAdapter extends RecyclerView.Adapter<AttendanceEntryAdapter.EntryVH> {

    public static class Entry {
        public final String subject;
        public final String status;
        public final String time;
        public Entry(String subject, String status, String time) {
            this.subject = subject;
            this.status = status;
            this.time = time;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void setEntries(List<Entry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EntryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_entry, parent, false);
        return new EntryVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryVH holder, int position) {
        Entry e = entries.get(position);
        holder.tvSubject.setText(e.subject);
        holder.tvTime.setText(e.time == null ? "" : e.time);
        holder.chip.setText(e.status);
        if ("Present".equalsIgnoreCase(e.status)) {
            holder.chip.setBackgroundResource(R.drawable.status_chip_present);
        } else if ("Absent".equalsIgnoreCase(e.status) || "Not Marked".equalsIgnoreCase(e.status)) {
            holder.chip.setBackgroundResource(R.drawable.status_chip_absent);
        } else {
            holder.chip.setBackgroundResource(R.drawable.status_chip_present);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class EntryVH extends RecyclerView.ViewHolder {
        final TextView tvSubject;
        final TextView tvTime;
        final TextView chip;
        EntryVH(@NonNull View itemView) {
            super(itemView);
            tvSubject = itemView.findViewById(R.id.tv_subject);
            tvTime = itemView.findViewById(R.id.tv_time);
            chip = itemView.findViewById(R.id.chip_status);
        }
    }
}


