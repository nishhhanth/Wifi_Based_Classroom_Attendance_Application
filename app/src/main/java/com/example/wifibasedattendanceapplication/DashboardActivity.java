package com.example.wifibasedattendanceapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends BaseAuthenticatedActivity {

    private TextView tvGreeting, tvStudentDetails, tvAcademicYear, tvAttendancePercentage, tvFeesStatus;
    private CardView cardAttendance, cardFees, cardMarkAttendance, cardHoliday, 
                     cardAiAssistant, cardLogout;
    
    private String currentStudentEnrollment;
    private DatabaseReference studentsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initViews();
        setupClickListeners();
        getCurrentStudentEnrollment();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh attendance and fees data when activity resumes
        if (currentStudentEnrollment != null) {
            calculateAttendancePercentage();
            loadFeesStatus();
        }
    }

    private void initViews() {
        // Header views
        tvGreeting = findViewById(R.id.tv_greeting);
        tvStudentDetails = findViewById(R.id.tv_student_details);
        tvAcademicYear = findViewById(R.id.tv_academic_year);
        tvAttendancePercentage = findViewById(R.id.tv_attendance_percentage);
        tvFeesStatus = findViewById(R.id.tv_fees_status);
        
        // Initialize Firebase reference
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Card views
        cardAttendance = findViewById(R.id.card_attendance);
        cardFees = findViewById(R.id.card_fees);
        cardMarkAttendance = findViewById(R.id.card_mark_attendance);
        cardHoliday = findViewById(R.id.card_holiday);
        cardAiAssistant = findViewById(R.id.card_ai_assistant);
        cardLogout = findViewById(R.id.card_logout);

        // Profile image opens profile page
        View ivProfile = findViewById(R.id.iv_profile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        }
    }

    private void setupClickListeners() {
        // Key metrics cards
        cardAttendance.setOnClickListener(v -> {
            // Open detailed overview screen
            startActivity(new Intent(this, AttendanceOverviewActivity.class));
        });

        cardFees.setOnClickListener(v -> {
            // Navigate to FeesActivity
            startActivity(new Intent(this, FeesActivity.class));
        });

        // Action cards
        cardMarkAttendance.setOnClickListener(v -> {
            showToast("Opening Mark Attendance...");
            // Navigate to SubmitAttendanceActivity for marking attendance
            startActivity(new Intent(this, SubmitAttendanceActivity.class));
        });

        cardHoliday.setOnClickListener(v -> {
            startActivity(new Intent(this, HolidayAndAttendanceCalendarActivity.class));
        });

        cardAiAssistant.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatActivity.class));
        });

        cardLogout.setOnClickListener(v -> {
            showLogoutDialog();
        });
    }

    private void getCurrentStudentEnrollment() {
        // Get current user's email
        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                          FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
        
        if (userEmail == null) {
            Log.e("Dashboard", "User not authenticated");
            loadDefaultUserData();
            return;
        }
        
        Log.d("Dashboard", "Searching for student with email: " + userEmail);
        
        // Search for student enrollment in Firebase
        studentsRef.orderByChild("student_email").equalTo(userEmail)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                            currentStudentEnrollment = studentSnapshot.getKey();
                            Log.d("Dashboard", "Found student enrollment: " + currentStudentEnrollment);
                            
                            // Load student data and calculate attendance
                            loadStudentData(studentSnapshot);
                            calculateAttendancePercentage();
                            loadFeesStatus();
                            break; // Get the first match
                        }
                    } else {
                        Log.e("Dashboard", "No student found with email: " + userEmail);
                        loadDefaultUserData();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Dashboard", "Error searching for student: " + error.getMessage());
                    loadDefaultUserData();
                }
            });
    }
    
    private void loadStudentData(DataSnapshot studentSnapshot) {
        String studentName = studentSnapshot.child("student_name").getValue(String.class);
        String division = studentSnapshot.child("Division").getValue(String.class);
        
        // Update UI with real student data
        if (studentName != null) {
            tvGreeting.setText("Hi " + studentName);
        } else {
            tvGreeting.setText("Hi Student");
        }
        
        if (division != null) {
            tvStudentDetails.setText("Division " + division);
        } else {
            tvStudentDetails.setText("Student Details");
        }
        
        if (tvAcademicYear != null) {
            tvAcademicYear.setText("2024-2025"); // Optional if view present
        }
    }
    
    private void loadDefaultUserData() {
        // Set default values when student data is not available
        tvGreeting.setText("Hi Student");
        tvStudentDetails.setText("Student Details");
        if (tvAcademicYear != null) {
            tvAcademicYear.setText("2024-2025");
        }
        tvAttendancePercentage.setText("N/A");
        tvFeesStatus.setText("N/A");
    }
    
    private void calculateAttendancePercentage() {
        if (currentStudentEnrollment == null) {
            Log.e("Dashboard", "Student enrollment not available");
            tvAttendancePercentage.setText("N/A");
            return;
        }
        
        Log.d("Dashboard", "Calculating attendance for student: " + currentStudentEnrollment);
        
        // Get student's attendance records
        DatabaseReference studentAttendanceRef = studentsRef
            .child(currentStudentEnrollment)
            .child("Attendance");
            
        studentAttendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    final int[] totalSessions = {0};
                    final int[] presentSessions = {0};
                    
                    Log.d("Dashboard", "Found " + dataSnapshot.getChildrenCount() + " attendance records");
                    
                    // Get all session IDs first
                    List<String> sessionIds = new ArrayList<>();
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        sessionIds.add(sessionSnapshot.getKey());
                    }
                    
                    if (sessionIds.isEmpty()) {
                        tvAttendancePercentage.setText("0.00 %");
                        return;
                    }
                    
                    final int totalToFetch = sessionIds.size();
                    final int[] fetched = new int[]{0};
                    
                    // Process each session to check if it has valid subject data
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        String sessionId = sessionSnapshot.getKey();
                        String attendanceStatus = sessionSnapshot.getValue(String.class);
                        
                        // Check if this session has valid subject data
                        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("AttendanceReport");
                        reportsRef.child(sessionId).child("subject")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot subjectSnap) {
                                    String subject = subjectSnap.getValue(String.class);
                                    
                                    // Only count sessions with valid subject data
                                    if (subject != null && !subject.isEmpty()) {
                                        totalSessions[0]++;
                                        
                                        Log.d("Dashboard", "Session " + sessionId + ": " + attendanceStatus + " (Subject: " + subject + ")");
                                        
                                        if ("P".equalsIgnoreCase(attendanceStatus) || "Present".equalsIgnoreCase(attendanceStatus)) {
                                            presentSessions[0]++;
                                        }
                                    }
                                    
                                    fetched[0] += 1;
                                    if (fetched[0] == totalToFetch) {
                                        // Calculate percentage
                                        double percentage = 0.0;
                                        if (totalSessions[0] > 0) {
                                            percentage = (double) presentSessions[0] / totalSessions[0] * 100;
                                        }
                                        
                                        // Update UI with calculated percentage
                                        String percentageText = String.format("%.2f %%", percentage);
                                        tvAttendancePercentage.setText(percentageText);
                                        
                                        Log.d("Dashboard", "Final calculation: " + presentSessions[0] + " present out of " + totalSessions[0] + " total = " + percentageText);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    fetched[0] += 1;
                                    if (fetched[0] == totalToFetch) {
                                        // Calculate percentage
                                        double percentage = 0.0;
                                        if (totalSessions[0] > 0) {
                                            percentage = (double) presentSessions[0] / totalSessions[0] * 100;
                                        }
                                        
                                        // Update UI with calculated percentage
                                        String percentageText = String.format("%.2f %%", percentage);
                                        tvAttendancePercentage.setText(percentageText);
                                        
                                        Log.d("Dashboard", "Final calculation: " + presentSessions[0] + " present out of " + totalSessions[0] + " total = " + percentageText);
                                    }
                                }
                            });
                    }
                } else {
                    Log.d("Dashboard", "No attendance records found for student: " + currentStudentEnrollment);
                    tvAttendancePercentage.setText("0.00 %");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Dashboard", "Error calculating attendance: " + error.getMessage());
                tvAttendancePercentage.setText("Error");
            }
        });
    }

    private void loadFeesStatus() {
        if (currentStudentEnrollment == null) {
            Log.e("Dashboard", "Student enrollment not available");
            tvFeesStatus.setText("N/A");
            return;
        }
        
        Log.d("Dashboard", "Loading fees status for student: " + currentStudentEnrollment);
        
        // Get student's fees data
        DatabaseReference studentFeesRef = studentsRef
            .child(currentStudentEnrollment)
            .child("Fees");
            
        studentFeesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String feeStatus = dataSnapshot.child("fee_status").getValue(String.class);
                    Integer totalFees = dataSnapshot.child("total_fees").getValue(Integer.class);
                    Integer paidFees = dataSnapshot.child("paid_fees").getValue(Integer.class);
                    Integer remainingFees = dataSnapshot.child("remaining_fees").getValue(Integer.class);
                    
                    if (feeStatus != null) {
                        tvFeesStatus.setText(feeStatus);
                        
                        // Set color based on fee status
                        if ("Fully Paid".equals(feeStatus)) {
                            tvFeesStatus.setTextColor(getResources().getColor(R.color.success_color));
                        } else if ("Overdue".equals(feeStatus)) {
                            tvFeesStatus.setTextColor(getResources().getColor(R.color.error_color));
                        } else if ("Partially Paid".equals(feeStatus)) {
                            tvFeesStatus.setTextColor(getResources().getColor(R.color.warning_color));
                        } else {
                            tvFeesStatus.setTextColor(getResources().getColor(R.color.text_primary));
                        }
                    } else {
                        tvFeesStatus.setText("No Data");
                    }
                    
                    Log.d("Dashboard", "Fees status loaded: " + feeStatus);
                } else {
                    Log.d("Dashboard", "No fees data found for student: " + currentStudentEnrollment);
                    tvFeesStatus.setText("No Data");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Dashboard", "Error loading fees status: " + error.getMessage());
                tvFeesStatus.setText("Error");
            }
        });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        // Show exit confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit the app?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    super.onBackPressed();
                })
                .setNegativeButton("No", null)
                .show();
    }
}
