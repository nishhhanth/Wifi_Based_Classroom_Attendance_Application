package com.example.wifibasedattendanceapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PaymentHistoryAdapter extends RecyclerView.Adapter<PaymentHistoryAdapter.PaymentHistoryViewHolder> {

    private List<Fees.PaymentHistory> paymentHistoryList;

    public PaymentHistoryAdapter(List<Fees.PaymentHistory> paymentHistoryList) {
        this.paymentHistoryList = paymentHistoryList;
    }

    @NonNull
    @Override
    public PaymentHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment_history, parent, false);
        return new PaymentHistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaymentHistoryViewHolder holder, int position) {
        Fees.PaymentHistory payment = paymentHistoryList.get(position);
        holder.bind(payment);
    }

    @Override
    public int getItemCount() {
        return paymentHistoryList != null ? paymentHistoryList.size() : 0;
    }

    public static class PaymentHistoryViewHolder extends RecyclerView.ViewHolder {
        private TextView tvPaymentId, tvAmount, tvPaymentDate, tvPaymentMethod, tvStatus, tvDescription;

        public PaymentHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPaymentId = itemView.findViewById(R.id.tv_payment_id);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            tvPaymentDate = itemView.findViewById(R.id.tv_payment_date);
            tvPaymentMethod = itemView.findViewById(R.id.tv_payment_method);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvDescription = itemView.findViewById(R.id.tv_description);
        }

        public void bind(Fees.PaymentHistory payment) {
            tvPaymentId.setText("Payment ID: " + payment.getPaymentId());
            tvAmount.setText("₹" + String.format("%,d", payment.getAmount()));
            tvPaymentDate.setText(payment.getPaymentDate());
            tvPaymentMethod.setText(payment.getPaymentMethod());
            tvStatus.setText(payment.getStatus());
            tvDescription.setText(payment.getDescription());

            // Set status color
            if ("Completed".equals(payment.getStatus())) {
                tvStatus.setTextColor(itemView.getContext().getResources().getColor(R.color.success_color));
            } else {
                tvStatus.setTextColor(itemView.getContext().getResources().getColor(R.color.error_color));
            }
        }
    }
}
