package com.example.wifibasedattendanceapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SelectAttendanceActivity extends AppCompatActivity {

    private static final String TAG = "SelectAttendance";
    
    private TextView dateTextView, timeTextView, enrollmentTextView, subjectTextView;
    private Button presentButton;
    
    private String sessionId;
    private String studentEnrollment;
    private DatabaseReference sessionRef;
    private DatabaseReference studentAttendanceRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_attendance);

        // Get session information from intent
        Intent intent = getIntent();
        if (intent != null) {
            sessionId = intent.getStringExtra("sessionId");
            studentEnrollment = intent.getStringExtra("studentEnrollment");
        }

        if (sessionId == null || studentEnrollment == null) {
            Toast.makeText(this, "Session information not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadSessionData();
        setupPresentButton();
        
        // Start monitoring session status in real-time
        startSessionMonitoring();
    }

    private void initViews() {
        dateTextView = findViewById(R.id.date_text);
        timeTextView = findViewById(R.id.time_text);
        enrollmentTextView = findViewById(R.id.enrollment_text);
        subjectTextView = findViewById(R.id.subject_text);
        presentButton = findViewById(R.id.btn_login);
    }

    private void loadSessionData() {
        sessionRef = FirebaseDatabase.getInstance().getReference("AttendanceReport").child(sessionId);
        
        // Show loading state
        showLoadingState();
        
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Load session details
                    String branch = safeString(dataSnapshot.child("branch").getValue());
                    String division = safeString(dataSnapshot.child("division").getValue());
                    String group = safeString(dataSnapshot.child("group").getValue());
                    String subject = safeString(dataSnapshot.child("subject").getValue());
                    String periodDate = safeString(dataSnapshot.child("period_date").getValue());
                    String startTime = safeString(dataSnapshot.child("start_time").getValue());
                    String endTime = safeString(dataSnapshot.child("end_time").getValue());
                    String sessionStatus = safeString(dataSnapshot.child("session_status").getValue());
                    Long endTimestamp = dataSnapshot.child("end_timestamp").getValue(Long.class);
                    
                    // Validate session status and time
                    if (!validateSessionStatus(sessionStatus, endTimestamp)) {
                        return;
                    }
                    
                    // Validate required fields
                    if (subject.isEmpty() || periodDate.isEmpty() || startTime.isEmpty()) {
                        Toast.makeText(SelectAttendanceActivity.this, "Session data is incomplete!", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    
                    // Validate that student belongs to this session's division and group
                    validateStudentDivisionAndGroup(division, group);
                    
                    // Update UI with session data
                    updateUI(branch, division, group, subject, periodDate, startTime, endTime);
                    
                    // Check current attendance status
                    checkCurrentAttendanceStatus();
                } else {
                    Toast.makeText(SelectAttendanceActivity.this, "Session not found!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading session data: " + databaseError.getMessage());
                Toast.makeText(SelectAttendanceActivity.this, "Error loading session data!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    private void validateStudentDivisionAndGroup(String sessionDivision, String sessionGroup) {
        // Get student's division and group from Firebase
        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(studentEnrollment);
        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                if (studentSnapshot.exists()) {
                    String studentDivision = studentSnapshot.child("Division").getValue(String.class);
                    String studentGroup = studentSnapshot.child("Group").getValue(String.class);
                    
                    Log.d(TAG, "Student " + studentEnrollment + " has Division: " + studentDivision + ", Group: " + studentGroup);
                    Log.d(TAG, "Session has Division: " + sessionDivision + ", Group: " + sessionGroup);
                    
                    if (studentDivision == null || studentGroup == null) {
                        Toast.makeText(SelectAttendanceActivity.this, 
                            "Student division or group information is missing. Please contact administrator.", 
                            Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    
                    // Check if student belongs to this session's division and group
                    if (!sessionDivision.equals(studentDivision) || !sessionGroup.equals(studentGroup)) {
                        // Convert abbreviations to display names for user-friendly messages
                        String displaySessionDivision = convertAbbreviationToDisplayName(sessionDivision);
                        String displaySessionGroup = convertAbbreviationToDisplayName(sessionGroup);
                        String displayStudentDivision = convertAbbreviationToDisplayName(studentDivision);
                        String displayStudentGroup = convertAbbreviationToDisplayName(studentGroup);
                        
                        String message = "You are not authorized to mark attendance for this session. " +
                                       "This session is for Division " + displaySessionDivision + " and Group " + displaySessionGroup + 
                                       ", but you belong to Division " + displayStudentDivision + " and Group " + displayStudentGroup + ".";
                        Toast.makeText(SelectAttendanceActivity.this, message, Toast.LENGTH_LONG).show();
                        Log.w(TAG, "Student division/group mismatch: " + message);
                        finish();
                        return;
                    }
                    
                    Log.d(TAG, "Student division and group validation passed");
                } else {
                    Toast.makeText(SelectAttendanceActivity.this, 
                        "Student information not found. Please contact administrator.", 
                        Toast.LENGTH_LONG).show();
                    finish();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error validating student division and group: " + error.getMessage());
                Toast.makeText(SelectAttendanceActivity.this, 
                    "Error validating student information. Please try again.", 
                    Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    /**
     * Converts abbreviated division/group values to display names for user-friendly messages
     */
    private String convertAbbreviationToDisplayName(String abbreviation) {
        if (abbreviation == null) return "Unknown";
        
        // Convert "A" to "Div - 1", "B" to "Div - 2", etc.
        if ("A".equals(abbreviation)) return "Div - 1";
        if ("B".equals(abbreviation)) return "Div - 2";
        if ("C".equals(abbreviation)) return "Div - 3";
        if ("D".equals(abbreviation)) return "Div - 4";
        if ("E".equals(abbreviation)) return "Div - 5";
        
        // For groups, just return as-is since they're already numbers
        return abbreviation;
    }

    private boolean validateSessionStatus(String sessionStatus, Long endTimestamp) {
        long currentTime = System.currentTimeMillis();
        
        // Check if session is active
        if (!"active".equals(sessionStatus)) {
            String message = "Session is not active. Status: " + sessionStatus;
            if ("ended".equals(sessionStatus)) {
                message = "Faculty has ended this attendance session. You can no longer mark attendance.";
            } else if ("expired".equals(sessionStatus)) {
                message = "This attendance session has expired. You can no longer mark attendance.";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        
        // Check if session has expired based on end timestamp
        if (endTimestamp != null && currentTime > endTimestamp) {
            // Allow a grace period of 30 minutes after the official end time
            long gracePeriod = 30 * 60 * 1000; // 30 minutes in milliseconds
            if (currentTime > (endTimestamp + gracePeriod)) {
                Toast.makeText(this, "Attendance session has expired. You can no longer mark attendance.", Toast.LENGTH_LONG).show();
                finish();
                return false;
            } else {
                Log.d(TAG, "Session is within grace period. End time: " + endTimestamp + 
                      ", Current time: " + currentTime + ", Grace period until: " + (endTimestamp + gracePeriod));
            }
        }
        
        Log.d(TAG, "Session validation passed. Status: " + sessionStatus + ", Current time: " + currentTime + ", End time: " + endTimestamp);
        return true;
    }

    private void showLoadingState() {
        if (dateTextView != null) dateTextView.setText("Loading...");
        if (timeTextView != null) timeTextView.setText("Loading...");
        if (enrollmentTextView != null) enrollmentTextView.setText("Loading...");
        if (subjectTextView != null) subjectTextView.setText("Loading...");
        if (presentButton != null) {
            presentButton.setText("Loading...");
            presentButton.setEnabled(false);
        }
    }

    private void updateUI(String branch, String division, String group, String subject, 
                         String periodDate, String startTime, String endTime) {
        // Update date and time
        if (dateTextView != null) {
            dateTextView.setText(periodDate);
        }
        if (timeTextView != null) {
            timeTextView.setText(startTime + " - " + endTime);
        }
        
        // Update enrollment
        if (enrollmentTextView != null) {
            enrollmentTextView.setText(studentEnrollment);
        }
        
        // Update subject
        if (subjectTextView != null) {
            subjectTextView.setText(subject);
        }
        
        Log.d(TAG, "UI updated with session data: " + subject + " on " + periodDate + " at " + startTime + "-" + endTime);
    }

    private void checkCurrentAttendanceStatus() {
        // Check if student has already marked attendance
        studentAttendanceRef = FirebaseDatabase.getInstance().getReference("Students")
            .child(studentEnrollment)
            .child("Attendance")
            .child(sessionId);
            
        studentAttendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String status = safeString(dataSnapshot.getValue());
                    if ("P".equals(status)) {
                        // Already marked present
                        presentButton.setText("Already Marked Present");
                        presentButton.setEnabled(false);
                        Toast.makeText(SelectAttendanceActivity.this, 
                            "You have already marked your attendance for this session!", Toast.LENGTH_LONG).show();
                    } else if ("A".equals(status)) {
                        // Marked absent (can still mark present)
                        presentButton.setText("Mark Present");
                        presentButton.setEnabled(true);
                    }
                } else {
                    // Not marked yet
                    presentButton.setText("Mark Present");
                    presentButton.setEnabled(true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error checking attendance status: " + databaseError.getMessage());
            }
        });
    }

    private void setupPresentButton() {
        presentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Require strong biometric authentication before marking attendance
                if (!BiometricAuthUtil.isStrongBiometricAvailable(SelectAttendanceActivity.this)) {
                    Toast.makeText(SelectAttendanceActivity.this,
                            "Face/Fingerprint not set up. Please enroll a biometric in device settings.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                presentButton.setEnabled(false);
                presentButton.setText("Authenticating...");

                BiometricAuthUtil.authenticate(SelectAttendanceActivity.this,
                        "Verify identity",
                        "Authenticate to mark your attendance",
                        new BiometricAuthUtil.Callback() {
                            @Override
                            public void onAuthenticated() {
                                markAttendancePresent();
                            }

                            @Override
                            public void onFailed(@NonNull String reason) {
                                Toast.makeText(SelectAttendanceActivity.this,
                                        "Authentication failed. Please try again.",
                                        Toast.LENGTH_SHORT).show();
                                presentButton.setEnabled(true);
                                presentButton.setText("Mark Present");
                            }

                            @Override
                            public void onError(int code, @NonNull String message) {
                                Toast.makeText(SelectAttendanceActivity.this,
                                        message,
                                        Toast.LENGTH_SHORT).show();
                                presentButton.setEnabled(true);
                                presentButton.setText("Mark Present");
                            }
                        });
            }
        });
    }

    private void markAttendancePresent() {
        if (studentAttendanceRef == null) {
            Toast.makeText(this, "Error: Attendance reference not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Marking attendance for student " + studentEnrollment + " in session " + sessionId);

        // Disable button to prevent multiple clicks
        presentButton.setEnabled(false);
        presentButton.setText("Marking Attendance...");

        // Mark attendance as present in both locations
        studentAttendanceRef.setValue("P").addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Successfully marked student attendance as Present");
                
                // Also update the session-level attendance
                DatabaseReference sessionAttendanceRef = FirebaseDatabase.getInstance()
                    .getReference("AttendanceReport")
                    .child(sessionId)
                    .child("Students")
                    .child(studentEnrollment)
                    .child("attendance_status");
                    
                sessionAttendanceRef.setValue("Present").addOnCompleteListener(sessionTask -> {
                    if (sessionTask.isSuccessful()) {
                        Log.d(TAG, "Successfully updated session-level attendance");
                        
                        // Success - show confirmation
                        presentButton.setText("Attendance Marked!");
                        Toast.makeText(SelectAttendanceActivity.this, 
                            "Attendance marked successfully!", Toast.LENGTH_LONG).show();
                        
                        // Navigate to confirmation screen after delay
                        new android.os.Handler().postDelayed(() -> {
                            Intent intent = new Intent(SelectAttendanceActivity.this, 
                                activity_presence_recorded.class);
                            startActivity(intent);
                            finish();
                        }, 2000);
                    } else {
                        // Session update failed
                        Log.e(TAG, "Failed to update session attendance: " + sessionTask.getException());
                        Toast.makeText(SelectAttendanceActivity.this, 
                            "Attendance marked but session update failed!", Toast.LENGTH_SHORT).show();
                        presentButton.setEnabled(true);
                        presentButton.setText("Mark Present");
                    }
                });
            } else {
                // Student attendance update failed
                Log.e(TAG, "Failed to mark attendance: " + task.getException());
                Toast.makeText(SelectAttendanceActivity.this, 
                    "Failed to mark attendance. Please try again!", Toast.LENGTH_SHORT).show();
                presentButton.setEnabled(true);
                presentButton.setText("Mark Present");
            }
        });
    }

    private void startSessionMonitoring() {
        // Monitor session status changes in real-time
        DatabaseReference sessionStatusRef = FirebaseDatabase.getInstance()
            .getReference("AttendanceReport")
            .child(sessionId)
            .child("session_status");
            
        sessionStatusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String currentStatus = safeString(dataSnapshot.getValue());
                    Log.d(TAG, "Session status changed to: " + currentStatus);
                    
                    // If session is no longer active, prevent attendance marking
                    if (!"active".equals(currentStatus)) {
                        String message = "Session is no longer active. Status: " + currentStatus;
                        if ("ended".equals(currentStatus)) {
                            message = "Faculty has ended the attendance session. You can no longer mark attendance.";
                        } else if ("expired".equals(currentStatus)) {
                            message = "Attendance session has expired. You can no longer mark attendance.";
                        }
                        
                        Toast.makeText(SelectAttendanceActivity.this, message, Toast.LENGTH_LONG).show();
                        
                        // Disable attendance marking
                        if (presentButton != null) {
                            presentButton.setEnabled(false);
                            presentButton.setText("Session Ended");
                        }
                        
                        // Close activity after delay
                        new android.os.Handler().postDelayed(() -> {
                            finish();
                        }, 3000);
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error monitoring session status: " + databaseError.getMessage());
            }
        });
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}