package com.example.wifibasedattendanceapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class activity_session extends BaseAuthenticatedActivity {
    String branch, division, subject;
    private String sessionId;
    private static final String PREFS_NAME = "attendance_prefs";
    private static final String KEY_ACTIVE_SESSION_ID = "active_session_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);
        
        // Only check authentication when needed for database operations
        // Don't check immediately on create
        
        ImageView gifImageView = findViewById(R.id.loading);

        Button btn = findViewById(R.id.end_session);
        android.widget.Button applyBtn = findViewById(R.id.btn_apply_network_settings);
        android.widget.EditText ssidInput = findViewById(R.id.input_required_ssid);
        android.widget.CheckBox allowUniWifi = findViewById(R.id.chk_allow_university_wifi);

        Glide.with(this).asGif().load(R.drawable.loading).into(gifImageView);
        Intent intent = getIntent();
        if (intent != null) {
            branch = intent.getStringExtra("branch");
            division = intent.getStringExtra("division");
            subject = intent.getStringExtra("subject");
            // If coming to resume a session, set sessionId and skip creation
            String resumeSessionId = intent.getStringExtra("resumeSessionId");
            if (resumeSessionId != null && !resumeSessionId.trim().isEmpty()) {
                sessionId = resumeSessionId;
            }
        }
        // Try to resume existing active session if present, unless we were given an explicit sessionId
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            Log.d("Debug", "Explicit resume of session: " + sessionId);
            Toast.makeText(this, "Resumed existing attendance session", Toast.LENGTH_SHORT).show();
        } else {
            // Try to resume via preferences or create
            performDatabaseOperation(() -> tryResumeOrCreateSession());
        }

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Show confirmation dialog before ending session
                showEndSessionConfirmation();
            }
        });

        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sessionId == null || sessionId.trim().isEmpty()) {
                    Toast.makeText(activity_session.this, "Session not ready yet. Please wait...", Toast.LENGTH_SHORT).show();
                    return;
                }

                String ssid = ssidInput.getText() == null ? null : ssidInput.getText().toString().trim();
                boolean allowUniversity = allowUniWifi.isChecked();

                Map<String, Object> updates = new HashMap<>();
                if (ssid != null && !ssid.isEmpty()) {
                    updates.put("required_ssid", ssid);
                } else {
                    // Clear if empty input provided
                    updates.put("required_ssid", null);
                }
                updates.put("allow_university_wifi", allowUniversity);

                DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                        .getReference("AttendanceReport")
                        .child(sessionId);

                sessionRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(activity_session.this, "Network settings applied", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity_session.this, "Failed to apply settings", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showEndSessionConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("End Attendance Session")
            .setMessage("Are you sure you want to end this attendance session? Students will no longer be able to mark attendance.")
            .setPositiveButton("Yes, End Session", (dialog, which) -> {
                endSession();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void createSession() {
        Log.d("Debug", "createSession() called");
        
        // Check authentication before proceeding
        if (!isUserAuthenticated()) {
            Log.e("Debug", "User not authenticated, cannot create session");
            return;
        }

        // Generate a unique session ID using timestamp to prevent conflicts
        long timestamp = System.currentTimeMillis();
        sessionId = "attendance_session_id_" + timestamp;
        Log.d("Debug", "Generated unique session ID: " + sessionId);
        
        // Check if this session ID already exists (very unlikely but safe)
        checkSessionIdUniqueness();
    }

    private void tryResumeOrCreateSession() {
        // Check SharedPreferences for an active session id
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedSessionId = prefs.getString(KEY_ACTIVE_SESSION_ID, null);
        if (savedSessionId == null || savedSessionId.trim().isEmpty()) {
            Log.d("Debug", "No saved active session. Creating a new session.");
            createSession();
            return;
        }

        Log.d("Debug", "Attempting to resume saved session: " + savedSessionId);

        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(savedSessionId);

        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("session_status").getValue(String.class);
                    if ("active".equals(status)) {
                        sessionId = savedSessionId;
                        Log.d("Debug", "Resumed active session: " + sessionId);
                        Toast.makeText(activity_session.this, "Resumed existing attendance session", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d("Debug", "Saved session is not active (status=" + status + "). Creating new session.");
                        createSession();
                    }
                } else {
                    Log.d("Debug", "Saved session not found in database. Creating new session.");
                    createSession();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Debug", "Failed to check saved session: " + error.getMessage());
                // Fall back to creating a new session to not block faculty
                createSession();
            }
        });
    }
    
    /**
     * Checks if the generated session ID is unique
     * If not, generates a new one
     */
    private void checkSessionIdUniqueness() {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(sessionId);
                
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Session ID already exists (very unlikely), generate a new one
                    Log.w("Debug", "Session ID collision detected: " + sessionId + ", generating new ID");
                    long newTimestamp = System.currentTimeMillis() + 1; // Add 1ms to ensure uniqueness
                    sessionId = "attendance_session_id_" + newTimestamp;
                    Log.d("Debug", "New unique session ID: " + sessionId);
                    createSessionData();
                } else {
                    // Session ID is unique, proceed
                    Log.d("Debug", "Session ID is unique: " + sessionId);
                    createSessionData();
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("Debug", "Error checking session ID uniqueness: " + error.getMessage());
                // Proceed anyway to avoid blocking
                createSessionData();
            }
        });
    }
    
    /**
     * Finds the next available session ID by checking existing IDs
     * This prevents conflicts and ensures unique session IDs
     */
    private long findNextAvailableSessionId(DataSnapshot dataSnapshot) {
        long nextId = 1;
        
        // Check all existing session IDs to find the next available one
        for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
            String sessionKey = sessionSnapshot.getKey();
            if (sessionKey != null && sessionKey.startsWith("attendance_session_id_")) {
                try {
                    // Extract the numeric part from "attendance_session_id_X"
                    String numericPart = sessionKey.substring("attendance_session_id_".length());
                    long existingId = Long.parseLong(numericPart);
                    
                    // Update nextId to be one more than the highest existing ID
                    if (existingId >= nextId) {
                        nextId = existingId + 1;
                    }
                } catch (NumberFormatException e) {
                    Log.w("Debug", "Invalid session ID format: " + sessionKey);
                }
            }
        }
        
        Log.d("Debug", "Next available session ID: " + nextId);
        return nextId;
    }
    
    /**
     * Cleans up orphaned or invalid sessions
     * This helps prevent session ID conflicts
     */
    private void cleanupOrphanedSessions() {
        DatabaseReference attendanceReportRef = FirebaseDatabase.getInstance().getReference("AttendanceReport");
        attendanceReportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Log.d("Debug", "Checking for orphaned sessions...");
                    
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        String sessionKey = sessionSnapshot.getKey();
                        String sessionStatus = sessionSnapshot.child("session_status").getValue(String.class);
                        Long timestamp = sessionSnapshot.child("timestamp").getValue(Long.class);
                        
                        // Check for sessions without proper data
                        if (sessionStatus == null || timestamp == null) {
                            Log.w("Debug", "Found orphaned session: " + sessionKey + 
                                  " (status: " + sessionStatus + ", timestamp: " + timestamp + ")");
                            
                            // Optionally remove orphaned sessions
                            // Uncomment the next line if you want to auto-remove them
                            // sessionSnapshot.getRef().removeValue();
                        }
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("Debug", "Error checking for orphaned sessions: " + error.getMessage());
            }
        });
    }

    private void createSessionData() {
        DatabaseReference newReportRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(sessionId);

        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");
        String formattedDate = dateFormat.format(currentDate);

        Calendar calendar = Calendar.getInstance();
        String amPm = calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
        String startTime = calendar.get(Calendar.HOUR_OF_DAY) + " " + amPm;
        int endTime = calendar.get(Calendar.HOUR_OF_DAY) + 1;
        if (endTime > 12) {
            endTime = endTime - 12;
        }
        String s_endTime = endTime + " " + amPm;

        // Calculate actual end time in milliseconds
        long startTimeMillis = System.currentTimeMillis();
        long endTimeMillis = startTimeMillis + (60 * 60 * 1000); // 1 hour from start

        // Convert full names to abbreviated values for database consistency
        String abbreviatedDivision = convertDivisionToAbbreviation(division);

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("branch", branch);
        sessionData.put("division", abbreviatedDivision); // Use abbreviated value
        sessionData.put("period_date", formattedDate);
        sessionData.put("start_time", startTime);
        sessionData.put("end_time", s_endTime);
        sessionData.put("subject", subject);
        sessionData.put("timestamp", startTimeMillis);
        sessionData.put("end_timestamp", endTimeMillis);
        sessionData.put("session_status", "active"); // active, ended, or expired
        sessionData.put("start_time_millis", startTimeMillis);
        // Optional: faculty can pass hotspot SSID via intent extra "requiredSsid"
        try {
            String facultySsid = getIntent() != null ? getIntent().getStringExtra("requiredSsid") : null;
            if (facultySsid != null && !facultySsid.trim().isEmpty()) {
                sessionData.put("required_ssid", facultySsid);
            }
        } catch (Exception ignored) { }

        newReportRef.setValue(sessionData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("Debug", "Session data created successfully with ID: " + sessionId);
                // Persist active session id for resume
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_ACTIVE_SESSION_ID, sessionId)
                        .apply();
                createStudentsSectionInSession();
                
                // Clean up orphaned sessions in background
                cleanupOrphanedSessions();
            } else {
                Log.e("Debug", "Failed to create session: " + task.getException());
                Toast.makeText(activity_session.this, 
                    "Failed to create session. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Converts full division names to abbreviated values for database consistency
     */
    private String convertDivisionToAbbreviation(String fullDivision) {
        if (fullDivision == null) return "";
        
        // Convert "Div - 1" to "A", "Div - 2" to "B", etc.
        if (fullDivision.contains("Div - 1")) return "A";
        if (fullDivision.contains("Div - 2")) return "B";
        if (fullDivision.contains("Div - 3")) return "C";
        if (fullDivision.contains("Div - 4")) return "D";
        if (fullDivision.contains("Div - 5")) return "E";
        
        // If no match found, return the original value
        Log.w("Debug", "Unknown division format: " + fullDivision + ", using as-is");
        return fullDivision;
    }
    
    // Group removed: no conversion needed

    /**
     * Creates a Students section inside AttendanceReport/{sessionId}
     * Initializes each student as "Not Marked"
     * Only includes students from the specific division and group
     */
    private void createStudentsSectionInSession() {
        // Check authentication before proceeding
        if (!isUserAuthenticated()) {
            Log.e("Debug", "User not authenticated, cannot create students section");
            return;
        }
        
        // Convert full names to abbreviated values for comparison
        String abbreviatedDivision = convertDivisionToAbbreviation(division);
        
        Log.d("Debug", "Creating students section for session: " + sessionId + 
              " with division: " + division + " -> " + abbreviatedDivision);
        
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        DatabaseReference sessionStudentsRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(sessionId)
                .child("Students");

        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("Debug", "Found " + snapshot.getChildrenCount() + " total students");
                    
                    int initializedCount = 0;
                    int matchingStudentsCount = 0;
                    
                    for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                        String enrollmentNo = studentSnapshot.getKey();
                        String studentDivision = studentSnapshot.child("Division").getValue(String.class);
                        
                        Log.d("Debug", "Checking student: " + enrollmentNo + 
                              " (Division: " + studentDivision + ")");
                        
                        // Only include students from the specific division
                        if (abbreviatedDivision.equals(studentDivision)) {
                            matchingStudentsCount++;
                            Log.d("Debug", "Student " + enrollmentNo + " matches session criteria");

                            // Initialize session-level attendance as "Not Marked"
                            sessionStudentsRef.child(enrollmentNo).child("attendance_status").setValue("Not Marked")
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Log.d("Debug", "Successfully initialized session attendance for " + enrollmentNo);
                                    } else {
                                        Log.e("Debug", "Failed to initialize session attendance for " + enrollmentNo + ": " + task.getException());
                                    }
                                });

                            // ALSO initialize student's Attendance node with "A" (default absent)
                            FirebaseDatabase.getInstance().getReference("Students")
                                    .child(enrollmentNo)
                                    .child("Attendance")
                                    .child(sessionId)
                                    .setValue("A") // default absent until marked
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            Log.d("Debug", "Successfully initialized student attendance for " + enrollmentNo);
                                        } else {
                                            Log.e("Debug", "Failed to initialize student attendance for " + enrollmentNo + ": " + task.getException());
                                        }
                                    });
                            
                            initializedCount++;
                        } else {
                            Log.d("Debug", "Student " + enrollmentNo + " does not match session criteria " +
                                  "(Division: " + abbreviatedDivision + " vs " + studentDivision + ")");
                        }
                    }
                    
                    Log.d("Debug", "Students section initialization completed. " +
                          "Total students: " + snapshot.getChildrenCount() + 
                          ", Matching students: " + matchingStudentsCount + 
                          ", Initialized: " + initializedCount);
                    
                    if (matchingStudentsCount == 0) {
                        Log.w("Debug", "No students found matching division: " + abbreviatedDivision);
                        Toast.makeText(activity_session.this, 
                            "Warning: No students found for Division " + abbreviatedDivision, 
                            Toast.LENGTH_LONG).show();
                    } else {
                        Log.d("Debug", "Successfully included " + matchingStudentsCount + " students in session");
                        Toast.makeText(activity_session.this, 
                            "Session created with " + matchingStudentsCount + " students from Division " + abbreviatedDivision, 
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w("Debug", "No students found in Students collection");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Debug", "Error creating students section: " + error.getMessage());
                handleDatabaseError(error);
            }
        });
    }

    private void endSession() {
        if (sessionId == null) {
            Toast.makeText(this, "Session not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("Debug", "Ending session: " + sessionId);
        
        // Update session status to ended
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(sessionId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("session_status", "ended");
        updates.put("end_timestamp", System.currentTimeMillis());
        
        sessionRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("Debug", "Session ended successfully");
                // Clear persisted active session id
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .remove(KEY_ACTIVE_SESSION_ID)
                        .apply();
                Toast.makeText(this, "Session ended. Generating attendance report...", Toast.LENGTH_SHORT).show();
                
                // Navigate to attendance report
                Intent intent = new Intent(getApplicationContext(), AttendanceReportActivity.class);
                intent.putExtra("sessionId", sessionId);
                startActivity(intent);
            } else {
                Log.e("Debug", "Failed to end session: " + task.getException());
                Toast.makeText(this, "Failed to end session. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
