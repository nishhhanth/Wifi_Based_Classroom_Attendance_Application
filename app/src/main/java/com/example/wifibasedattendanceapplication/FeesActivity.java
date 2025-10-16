package com.example.wifibasedattendanceapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FeesActivity extends BaseAuthenticatedActivity {

    private TextView tvStudentName, tvAcademicYear, tvTotalFees, tvPaidFees, tvRemainingFees;
    private TextView tvFeeStatus, tvNextDueDate, tvTuitionFee, tvLibraryFee, tvLaboratoryFee;
    private TextView tvExaminationFee, tvDevelopmentFee, tvSportsFee, tvTransportFee;
    private TextView tvScholarshipType, tvScholarshipAmount, tvScholarshipStatus, tvScholarshipDescription;
    
    private RecyclerView rvPaymentHistory, rvDueDates;
    private PaymentHistoryAdapter paymentHistoryAdapter;
    private DueDatesAdapter dueDatesAdapter;
    
    private Button btnPayFees, btnDownloadReceipt;
    private ImageView ivBack;
    
    private String currentStudentEnrollment;
    private DatabaseReference studentsRef;
    private Fees currentFees;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fees);

        initViews();
        setupClickListeners();
        setupRecyclerViews();
        getCurrentStudentEnrollment();
    }

    private void initViews() {
        // Header views
        tvStudentName = findViewById(R.id.tv_student_name);
        tvAcademicYear = findViewById(R.id.tv_academic_year);
        tvTotalFees = findViewById(R.id.tv_total_fees);
        tvPaidFees = findViewById(R.id.tv_paid_fees);
        tvRemainingFees = findViewById(R.id.tv_remaining_fees);
        
        // Fee status views
        tvFeeStatus = findViewById(R.id.tv_fee_status);
        tvNextDueDate = findViewById(R.id.tv_next_due_date);
        
        // Fee structure views
        tvTuitionFee = findViewById(R.id.tv_tuition_fee);
        tvLibraryFee = findViewById(R.id.tv_library_fee);
        tvLaboratoryFee = findViewById(R.id.tv_laboratory_fee);
        tvExaminationFee = findViewById(R.id.tv_examination_fee);
        tvDevelopmentFee = findViewById(R.id.tv_development_fee);
        tvSportsFee = findViewById(R.id.tv_sports_fee);
        tvTransportFee = findViewById(R.id.tv_transport_fee);
        
        // Scholarship views
        tvScholarshipType = findViewById(R.id.tv_scholarship_type);
        tvScholarshipAmount = findViewById(R.id.tv_scholarship_amount);
        tvScholarshipStatus = findViewById(R.id.tv_scholarship_status);
        tvScholarshipDescription = findViewById(R.id.tv_scholarship_description);
        
        // RecyclerViews
        rvPaymentHistory = findViewById(R.id.rv_payment_history);
        rvDueDates = findViewById(R.id.rv_due_dates);
        
        // Buttons
        btnPayFees = findViewById(R.id.btn_pay_fees);
        btnDownloadReceipt = findViewById(R.id.btn_download_receipt);
        ivBack = findViewById(R.id.iv_back);
        
        // Initialize Firebase reference
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
        
        btnPayFees.setOnClickListener(v -> {
            showToast("Payment gateway integration coming soon!");
            // TODO: Implement payment gateway integration
        });
        
        btnDownloadReceipt.setOnClickListener(v -> {
            showToast("Download receipt functionality coming soon!");
            // TODO: Implement receipt download functionality
        });
    }

    private void setupRecyclerViews() {
        // Payment History RecyclerView
        rvPaymentHistory.setLayoutManager(new LinearLayoutManager(this));
        paymentHistoryAdapter = new PaymentHistoryAdapter(new ArrayList<>());
        rvPaymentHistory.setAdapter(paymentHistoryAdapter);
        
        // Due Dates RecyclerView
        rvDueDates.setLayoutManager(new LinearLayoutManager(this));
        dueDatesAdapter = new DueDatesAdapter(new ArrayList<>());
        rvDueDates.setAdapter(dueDatesAdapter);
    }

    private void getCurrentStudentEnrollment() {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        Log.d("FeesActivity", "Searching for student with email: " + userEmail);
        
        // Search for student enrollment in Firebase
        studentsRef.orderByChild("student_email").equalTo(userEmail)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                            currentStudentEnrollment = studentSnapshot.getKey();
                            Log.d("FeesActivity", "Found student enrollment: " + currentStudentEnrollment);
                            
                            // Load student data and fees
                            loadStudentData(studentSnapshot);
                            break; // Get the first match
                        }
                    } else {
                        Log.e("FeesActivity", "No student found with email: " + userEmail);
                        loadDefaultData();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("FeesActivity", "Error searching for student: " + error.getMessage());
                    loadDefaultData();
                }
            });
    }

    private void loadStudentData(DataSnapshot studentSnapshot) {
        try {
            // Load student basic info
            String studentName = studentSnapshot.child("student_name").getValue(String.class);
            String rollNo = studentSnapshot.child("roll_no").getValue(String.class);
            
            if (studentName != null) {
                tvStudentName.setText(studentName + " (Roll: " + rollNo + ")");
            }
            
            // Load fees data
            DataSnapshot feesSnapshot = studentSnapshot.child("Fees");
            if (feesSnapshot.exists()) {
                loadFeesData(feesSnapshot);
            } else {
                Log.w("FeesActivity", "No fees data found for student");
                loadDefaultData();
            }
            
        } catch (Exception e) {
            Log.e("FeesActivity", "Error loading student data: " + e.getMessage());
            loadDefaultData();
        }
    }

    private void loadFeesData(DataSnapshot feesSnapshot) {
        try {
            // Load basic fee information
            String academicYear = feesSnapshot.child("academic_year").getValue(String.class);
            Integer totalFees = feesSnapshot.child("total_fees").getValue(Integer.class);
            Integer paidFees = feesSnapshot.child("paid_fees").getValue(Integer.class);
            Integer remainingFees = feesSnapshot.child("remaining_fees").getValue(Integer.class);
            String feeStatus = feesSnapshot.child("fee_status").getValue(String.class);
            String nextDueDate = feesSnapshot.child("next_due_date").getValue(String.class);
            
            // Update basic info
            if (academicYear != null) {
                tvAcademicYear.setText("Academic Year: " + academicYear);
            }
            if (totalFees != null) {
                tvTotalFees.setText("₹" + String.format("%,d", totalFees));
            }
            if (paidFees != null) {
                tvPaidFees.setText("₹" + String.format("%,d", paidFees));
            }
            if (remainingFees != null) {
                tvRemainingFees.setText("₹" + String.format("%,d", remainingFees));
            }
            if (feeStatus != null) {
                tvFeeStatus.setText(feeStatus);
            }
            if (nextDueDate != null && !"N/A".equals(nextDueDate)) {
                tvNextDueDate.setText("Next Due Date: " + nextDueDate);
            } else {
                tvNextDueDate.setText("All fees paid!");
            }
            
            // Load fee structure
            loadFeeStructure(feesSnapshot.child("fee_structure"));
            
            // Load payment history
            loadPaymentHistory(feesSnapshot.child("payment_history"));
            
            // Load due dates
            loadDueDates(feesSnapshot.child("due_dates"));
            
            // Load scholarship info
            loadScholarshipInfo(feesSnapshot.child("scholarship"));
            
        } catch (Exception e) {
            Log.e("FeesActivity", "Error loading fees data: " + e.getMessage());
            loadDefaultData();
        }
    }

    private void loadFeeStructure(DataSnapshot feeStructureSnapshot) {
        if (feeStructureSnapshot.exists()) {
            Integer tuitionFee = feeStructureSnapshot.child("tuition_fee").getValue(Integer.class);
            Integer libraryFee = feeStructureSnapshot.child("library_fee").getValue(Integer.class);
            Integer laboratoryFee = feeStructureSnapshot.child("laboratory_fee").getValue(Integer.class);
            Integer examinationFee = feeStructureSnapshot.child("examination_fee").getValue(Integer.class);
            Integer developmentFee = feeStructureSnapshot.child("development_fee").getValue(Integer.class);
            Integer sportsFee = feeStructureSnapshot.child("sports_fee").getValue(Integer.class);
            Integer transportFee = feeStructureSnapshot.child("transport_fee").getValue(Integer.class);
            
            if (tuitionFee != null) tvTuitionFee.setText("₹" + String.format("%,d", tuitionFee));
            if (libraryFee != null) tvLibraryFee.setText("₹" + String.format("%,d", libraryFee));
            if (laboratoryFee != null) tvLaboratoryFee.setText("₹" + String.format("%,d", laboratoryFee));
            if (examinationFee != null) tvExaminationFee.setText("₹" + String.format("%,d", examinationFee));
            if (developmentFee != null) tvDevelopmentFee.setText("₹" + String.format("%,d", developmentFee));
            if (sportsFee != null) tvSportsFee.setText("₹" + String.format("%,d", sportsFee));
            if (transportFee != null) tvTransportFee.setText("₹" + String.format("%,d", transportFee));
        }
    }

    private void loadPaymentHistory(DataSnapshot paymentHistorySnapshot) {
        List<Fees.PaymentHistory> paymentHistoryList = new ArrayList<>();
        
        if (paymentHistorySnapshot.exists()) {
            for (DataSnapshot paymentSnapshot : paymentHistorySnapshot.getChildren()) {
                try {
                    String paymentId = paymentSnapshot.child("payment_id").getValue(String.class);
                    Integer amount = paymentSnapshot.child("amount").getValue(Integer.class);
                    String paymentDate = paymentSnapshot.child("payment_date").getValue(String.class);
                    String paymentMethod = paymentSnapshot.child("payment_method").getValue(String.class);
                    String transactionId = paymentSnapshot.child("transaction_id").getValue(String.class);
                    String status = paymentSnapshot.child("status").getValue(String.class);
                    String description = paymentSnapshot.child("description").getValue(String.class);
                    
                    if (paymentId != null && amount != null) {
                        Fees.PaymentHistory payment = new Fees.PaymentHistory(
                            paymentId, amount, paymentDate, paymentMethod, 
                            transactionId, status, description
                        );
                        paymentHistoryList.add(payment);
                    }
                } catch (Exception e) {
                    Log.e("FeesActivity", "Error loading payment history item: " + e.getMessage());
                }
            }
        }
        
        paymentHistoryAdapter = new PaymentHistoryAdapter(paymentHistoryList);
        rvPaymentHistory.setAdapter(paymentHistoryAdapter);
    }

    private void loadDueDates(DataSnapshot dueDatesSnapshot) {
        List<Fees.DueDate> dueDatesList = new ArrayList<>();
        
        if (dueDatesSnapshot.exists()) {
            for (DataSnapshot dueDateSnapshot : dueDatesSnapshot.getChildren()) {
                try {
                    Integer installment = dueDateSnapshot.child("installment").getValue(Integer.class);
                    Integer amount = dueDateSnapshot.child("amount").getValue(Integer.class);
                    String dueDate = dueDateSnapshot.child("due_date").getValue(String.class);
                    String status = dueDateSnapshot.child("status").getValue(String.class);
                    String description = dueDateSnapshot.child("description").getValue(String.class);
                    
                    if (installment != null && amount != null) {
                        Fees.DueDate dueDateObj = new Fees.DueDate(
                            installment, amount, dueDate, status, description
                        );
                        dueDatesList.add(dueDateObj);
                    }
                } catch (Exception e) {
                    Log.e("FeesActivity", "Error loading due date item: " + e.getMessage());
                }
            }
        }
        
        dueDatesAdapter = new DueDatesAdapter(dueDatesList);
        rvDueDates.setAdapter(dueDatesAdapter);
    }

    private void loadScholarshipInfo(DataSnapshot scholarshipSnapshot) {
        if (scholarshipSnapshot.exists()) {
            String type = scholarshipSnapshot.child("type").getValue(String.class);
            Integer amount = scholarshipSnapshot.child("amount").getValue(Integer.class);
            String status = scholarshipSnapshot.child("status").getValue(String.class);
            String description = scholarshipSnapshot.child("description").getValue(String.class);
            
            if (type != null) tvScholarshipType.setText(type);
            if (amount != null && amount > 0) {
                tvScholarshipAmount.setText("₹" + String.format("%,d", amount));
            } else {
                tvScholarshipAmount.setText("₹0");
            }
            if (status != null) tvScholarshipStatus.setText(status);
            if (description != null) tvScholarshipDescription.setText(description);
        }
    }

    private void loadDefaultData() {
        // Load default data when no student is found or error occurs
        tvStudentName.setText("Student Not Found");
        tvAcademicYear.setText("Academic Year: N/A");
        tvTotalFees.setText("₹0");
        tvPaidFees.setText("₹0");
        tvRemainingFees.setText("₹0");
        tvFeeStatus.setText("No Data");
        tvNextDueDate.setText("No Data Available");
        
        // Clear RecyclerViews
        paymentHistoryAdapter = new PaymentHistoryAdapter(new ArrayList<>());
        rvPaymentHistory.setAdapter(paymentHistoryAdapter);
        
        dueDatesAdapter = new DueDatesAdapter(new ArrayList<>());
        rvDueDates.setAdapter(dueDatesAdapter);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
