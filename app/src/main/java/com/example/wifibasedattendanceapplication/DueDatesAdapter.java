package com.example.wifibasedattendanceapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DueDatesAdapter extends RecyclerView.Adapter<DueDatesAdapter.DueDatesViewHolder> {

    private List<Fees.DueDate> dueDatesList;

    public DueDatesAdapter(List<Fees.DueDate> dueDatesList) {
        this.dueDatesList = dueDatesList;
    }

    @NonNull
    @Override
    public DueDatesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_due_date, parent, false);
        return new DueDatesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DueDatesViewHolder holder, int position) {
        Fees.DueDate dueDate = dueDatesList.get(position);
        holder.bind(dueDate);
    }

    @Override
    public int getItemCount() {
        return dueDatesList != null ? dueDatesList.size() : 0;
    }

    public static class DueDatesViewHolder extends RecyclerView.ViewHolder {
        private TextView tvInstallment, tvAmount, tvDueDate, tvStatus, tvDescription;

        public DueDatesViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInstallment = itemView.findViewById(R.id.tv_installment);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            tvDueDate = itemView.findViewById(R.id.tv_due_date);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvDescription = itemView.findViewById(R.id.tv_description);
        }

        public void bind(Fees.DueDate dueDate) {
            tvInstallment.setText("Installment " + dueDate.getInstallment());
            tvAmount.setText("₹" + String.format("%,d", dueDate.getAmount()));
            tvDueDate.setText(dueDate.getDueDate());
            tvStatus.setText(dueDate.getStatus());
            tvDescription.setText(dueDate.getDescription());

            // Set status color
            if ("Pending".equals(dueDate.getStatus())) {
                tvStatus.setTextColor(itemView.getContext().getResources().getColor(R.color.warning_color));
            } else if ("Overdue".equals(dueDate.getStatus())) {
                tvStatus.setTextColor(itemView.getContext().getResources().getColor(R.color.error_color));
            } else {
                tvStatus.setTextColor(itemView.getContext().getResources().getColor(R.color.success_color));
            }
        }
    }
}
