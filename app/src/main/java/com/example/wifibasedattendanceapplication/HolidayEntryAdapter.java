package com.example.wifibasedattendanceapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class HolidayEntryAdapter extends RecyclerView.Adapter<HolidayEntryAdapter.EntryVH> {

    public static class Entry {
        public final String holidayName;
        public final String holidayDate;
        public final String holidayType;
        
        public Entry(String holidayName, String holidayDate, String holidayType) {
            this.holidayName = holidayName;
            this.holidayDate = holidayDate;
            this.holidayType = holidayType;
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_holiday_entry, parent, false);
        return new EntryVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryVH holder, int position) {
        Entry e = entries.get(position);
        holder.tvHolidayName.setText(e.holidayName);
        holder.tvHolidayDate.setText(e.holidayDate);
        holder.chip.setText(e.holidayType);
        holder.chip.setBackgroundResource(R.drawable.status_chip_holiday);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class EntryVH extends RecyclerView.ViewHolder {
        final TextView tvHolidayName;
        final TextView tvHolidayDate;
        final TextView chip;
        
        EntryVH(@NonNull View itemView) {
            super(itemView);
            tvHolidayName = itemView.findViewById(R.id.tv_holiday_name);
            tvHolidayDate = itemView.findViewById(R.id.tv_holiday_date);
            chip = itemView.findViewById(R.id.chip_holiday_type);
        }
    }
}
