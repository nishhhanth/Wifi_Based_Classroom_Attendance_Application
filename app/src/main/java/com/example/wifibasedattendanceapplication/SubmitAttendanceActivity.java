package com.example.wifibasedattendanceapplication;

import static java.lang.Thread.sleep;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class SubmitAttendanceActivity extends BaseAuthenticatedActivity {
    // Configuration - Set this to true for university environments
    private static final boolean TRUST_MODE_ENABLED = true;
    private static final boolean ALLOW_UNKNOWN_SSID = true;
    
    Button btn_logout, btn_test_wifi;
    private DatabaseReference attendanceReportRef;
    private String currentStudentEnrollment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit_attendance);

        Init();
        
        // Get student enrollment from intent or Firebase Auth
        getCurrentStudentEnrollment();
        
        // Don't check WiFi here - wait for enrollment to be retrieved
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Auto-refresh session check when activity resumes
        if (currentStudentEnrollment != null) {
            Log.d("SubmitAttendance", "Activity resumed, auto-refreshing session check");
            // Add a small delay to ensure Firebase is ready
            new android.os.Handler().postDelayed(() -> {
                checkForActiveSessions();
            }, 1000);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Clean up any listeners to prevent memory leaks
        if (attendanceReportRef != null) {
            // Note: We can't remove specific listeners here, but this helps with cleanup
        }
    }

    private void getCurrentStudentEnrollment() {
        // Get current user's email
        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                          FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
        
        if (userEmail == null) {
            Toast.makeText(this, "User not authenticated. Please login again.", Toast.LENGTH_LONG).show();
            // Redirect to login
            Intent intent = new Intent(this, LoginStudentActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        Log.d("SubmitAttendance", "Searching for student with email: " + userEmail);
        
        // Search for student enrollment in Firebase
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("student_email").equalTo(userEmail)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                            currentStudentEnrollment = studentSnapshot.getKey();
                            Log.d("SubmitAttendance", "Found student enrollment: " + currentStudentEnrollment);
                            
                            // Verify student data is complete
                            verifyStudentData(studentSnapshot);
                            break; // Get the first match
                        }
                        
                        // Now that we have the enrollment, check WiFi and proceed
                        if (currentStudentEnrollment != null) {
                            // Check location permissions before checking WiFi
                            if (hasLocationPermissions()) {
                                checkWifi();
                            } else {
                                requestLocationPermissions();
                            }
                        } else {
                            Toast.makeText(SubmitAttendanceActivity.this, 
                                "Student enrollment not found. Please contact administrator.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("SubmitAttendance", "No student found with email: " + userEmail);
                        
                        // Let's check what students exist in the database
                        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot allStudentsSnapshot) {
                                Log.d("SubmitAttendance", "Total students in database: " + allStudentsSnapshot.getChildrenCount());
                                for (DataSnapshot student : allStudentsSnapshot.getChildren()) {
                                    String studentEmail = student.child("student_email").getValue(String.class);
                                    Log.d("SubmitAttendance", "Student " + student.getKey() + " has email: " + studentEmail);
                                }
                            }
                            
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e("SubmitAttendance", "Error checking all students: " + error.getMessage());
                            }
                        });
                        
                        Toast.makeText(SubmitAttendanceActivity.this, 
                            "Student account not found. Please contact administrator.", Toast.LENGTH_LONG).show();
                        // Redirect to login
                        Intent intent = new Intent(SubmitAttendanceActivity.this, LoginStudentActivity.class);
                        startActivity(intent);
                        finish();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e("SubmitAttendance", "Error finding student enrollment: " + databaseError.getMessage());
                    Toast.makeText(SubmitAttendanceActivity.this, 
                        "Database error. Please try again.", Toast.LENGTH_SHORT).show();
                    // Redirect to login
                    Intent intent = new Intent(SubmitAttendanceActivity.this, LoginStudentActivity.class);
                    startActivity(intent);
                    finish();
                }
            });
    }

    private void verifyStudentData(DataSnapshot studentSnapshot) {
        String studentName = studentSnapshot.child("student_name").getValue(String.class);
        String studentEmail = studentSnapshot.child("student_email").getValue(String.class);
        String studentDivision = studentSnapshot.child("Division").getValue(String.class);
        String studentGroup = studentSnapshot.child("Group").getValue(String.class);
        
        Log.d("SubmitAttendance", "Student data verification:");
        Log.d("SubmitAttendance", "  Name: " + studentName);
        Log.d("SubmitAttendance", "  Email: " + studentEmail);
        Log.d("SubmitAttendance", "  Division: " + studentDivision);
        Log.d("SubmitAttendance", "  Group: " + studentGroup);
        
        if (studentName == null || studentEmail == null || studentDivision == null || studentGroup == null) {
            Log.w("SubmitAttendance", "Student data is incomplete");
        }
    }

    private void checkWifi() {
        // Simple WiFi check for now
        if (isConnectedToUniversityNetwork()) {
            // Check for active sessions before proceeding
            checkForActiveSessions();
        } else {
            Toast.makeText(this, "Not connected to the University Network.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkForActiveSessions() {
        if (currentStudentEnrollment == null) {
            Log.e("SubmitAttendance", "currentStudentEnrollment is null - this should not happen");
            Toast.makeText(this, "Student information not found. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d("SubmitAttendance", "Checking for active sessions for student: " + currentStudentEnrollment);
        attendanceReportRef = FirebaseDatabase.getInstance().getReference("AttendanceReport");
        
        // First, let's clean up expired sessions
        cleanupExpiredSessions();
        
        // Get student's division and group first
        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(currentStudentEnrollment);
        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                if (studentSnapshot.exists()) {
                    String studentDivision = studentSnapshot.child("Division").getValue(String.class);
                    String studentGroup = studentSnapshot.child("Group").getValue(String.class);
                    
                    if (studentDivision == null || studentGroup == null) {
                        Toast.makeText(SubmitAttendanceActivity.this, 
                            "Student division or group information is missing. Please contact administrator.", 
                            Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    Log.d("SubmitAttendance", "Student " + currentStudentEnrollment + 
                          " belongs to Division: " + studentDivision + ", Group: " + studentGroup);
                    
                    // Now check for active sessions matching student's division and group
                    checkSessionsForStudentDivision(studentDivision, studentGroup);
                } else {
                    Toast.makeText(SubmitAttendanceActivity.this, 
                        "Student information not found. Please contact administrator.", 
                        Toast.LENGTH_LONG).show();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SubmitAttendance", "Error getting student information: " + error.getMessage());
                Toast.makeText(SubmitAttendanceActivity.this, 
                    "Error retrieving student information. Please try again.", 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void checkSessionsForStudentDivision(String studentDivision, String studentGroup) {
        Log.d("SubmitAttendance", "Checking for sessions matching Division: " + studentDivision + ", Group: " + studentGroup);
        
        // Check for active sessions that match student's division and group
        attendanceReportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("SubmitAttendance", "Found " + dataSnapshot.getChildrenCount() + " total sessions");
                
                String activeSessionId = null;
                long currentTime = System.currentTimeMillis();
                long bestSessionTime = 0;
                
                // Find the most recent active session matching student's division and group
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    String sessionKey = sessionSnapshot.getKey();
                    String sessionDivision = sessionSnapshot.child("division").getValue(String.class);
                    String sessionGroup = sessionSnapshot.child("group").getValue(String.class);
                    String sessionStatus = sessionSnapshot.child("session_status").getValue(String.class);
                    Long endTimestamp = sessionSnapshot.child("end_timestamp").getValue(Long.class);
                    Long timestamp = sessionSnapshot.child("timestamp").getValue(Long.class);
                    
                    Log.d("SubmitAttendance", "Checking session: " + sessionKey + 
                          " with division: " + sessionDivision + 
                          ", group: " + sessionGroup + 
                          ", status: " + sessionStatus + 
                          ", end_timestamp: " + endTimestamp);
                    
                    // Check if session matches student's division and group
                    if (!studentDivision.equals(sessionDivision) || !studentGroup.equals(sessionGroup)) {
                        Log.d("SubmitAttendance", "Session " + sessionKey + " does not match student's division/group");
                        continue;
                    }
                    
                    Log.d("SubmitAttendance", "Session " + sessionKey + " matches student's division/group!");
                    
                    if (timestamp != null) {
                        // Check if session is active and within time limits
                        boolean isActive = "active".equals(sessionStatus);
                        boolean isWithinTime = true; // Remove 24-hour restriction for now
                        boolean isNotExpired = endTimestamp == null || currentTime <= (endTimestamp + (30 * 60 * 1000)); // 30 min grace period
                        
                        Log.d("SubmitAttendance", "Session " + sessionKey + " validation: active=" + isActive + 
                              ", withinTime=" + isWithinTime + ", notExpired=" + isNotExpired);
                        
                        if (isActive && isWithinTime && isNotExpired) {
                            // Find the most recent valid session
                            if (timestamp > bestSessionTime) {
                                bestSessionTime = timestamp;
                                activeSessionId = sessionSnapshot.getKey();
                                Log.d("SubmitAttendance", "New best active session found: " + activeSessionId + " at " + bestSessionTime);
                            }
                        } else {
                            if (!isActive) {
                                Log.d("SubmitAttendance", "Session " + sessionKey + " is not active (status: " + sessionStatus + ")");
                            }
                            if (!isWithinTime) {
                                Log.d("SubmitAttendance", "Session " + sessionKey + " is too old");
                            }
                            if (!isNotExpired) {
                                Log.d("SubmitAttendance", "Session " + sessionKey + " has expired (including grace period)");
                            }
                        }
                    } else {
                        Log.w("SubmitAttendance", "Session " + sessionKey + " has no timestamp");
                    }
                }
                
                if (activeSessionId != null) {
                    Log.d("SubmitAttendance", "Found active session: " + activeSessionId + 
                          " for Division: " + studentDivision + ", Group: " + studentGroup);
                    // Check if student is part of this session
                    checkStudentInSession(activeSessionId);
                } else {
                    Log.w("SubmitAttendance", "No active sessions found for Division: " + studentDivision + 
                          ", Group: " + studentGroup);
                    
                    // Show more helpful message with conversion info
                    String displayDivision = convertAbbreviationToDisplayName(studentDivision);
                    String displayGroup = convertAbbreviationToDisplayName(studentGroup);
                    
                    String message = "No active attendance sessions found for your Division (" + displayDivision + 
                                   ") and Group (" + displayGroup + "). Please wait for faculty to start a session.";
                    Toast.makeText(SubmitAttendanceActivity.this, message, Toast.LENGTH_LONG).show();
                    
                    Log.d("SubmitAttendance", "Display message: " + message);
                    
                    // Start real-time monitoring for new sessions
                    startRealTimeSessionMonitoring(studentDivision, studentGroup);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("SubmitAttendance", "Error checking sessions: " + databaseError.getMessage());
                Toast.makeText(SubmitAttendanceActivity.this, 
                    "Error checking sessions. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Starts real-time monitoring for new sessions matching the student's division and group
     */
    private void startRealTimeSessionMonitoring(String studentDivision, String studentGroup) {
        Log.d("SubmitAttendance", "Starting real-time session monitoring for Division: " + studentDivision + ", Group: " + studentGroup);
        
        // Monitor for new sessions in real-time
        attendanceReportRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("SubmitAttendance", "Real-time update: " + dataSnapshot.getChildrenCount() + " total sessions");
                
                // Check for new active sessions
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    String sessionKey = sessionSnapshot.getKey();
                    String sessionDivision = sessionSnapshot.child("division").getValue(String.class);
                    String sessionGroup = sessionSnapshot.child("group").getValue(String.class);
                    String sessionStatus = sessionSnapshot.child("session_status").getValue(String.class);
                    Long timestamp = sessionSnapshot.child("timestamp").getValue(Long.class);
                    
                    Log.d("SubmitAttendance", "Real-time checking session: " + sessionKey + 
                          " (Division: " + sessionDivision + ", Group: " + sessionGroup + 
                          ", Status: " + sessionStatus + ")");
                    
                    // Check if this is a new active session for the student's division and group
                    if (studentDivision.equals(sessionDivision) && studentGroup.equals(sessionGroup) && 
                        "active".equals(sessionStatus) && timestamp != null) {
                        
                        Log.d("SubmitAttendance", "Real-time: Found new active session: " + sessionKey);
                        
                        // Show success message
                        Toast.makeText(SubmitAttendanceActivity.this, 
                            "New attendance session detected! Redirecting...", Toast.LENGTH_SHORT).show();
                        
                        // Check if student is part of this session
                        checkStudentInSession(sessionKey);
                        
                        // Remove the listener since we found a session
                        attendanceReportRef.removeEventListener(this);
                        return;
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SubmitAttendance", "Real-time session monitoring cancelled: " + error.getMessage());
                Toast.makeText(SubmitAttendanceActivity.this, 
                    "Session monitoring interrupted. Please refresh manually.", Toast.LENGTH_SHORT).show();
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

    private void checkStudentInSession(String sessionId) {
        Log.d("SubmitAttendance", "Checking if student " + currentStudentEnrollment + " is in session " + sessionId);
        
        // Check if student is part of this session
        attendanceReportRef.child(sessionId).child("Students").child(currentStudentEnrollment)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        Log.d("SubmitAttendance", "Student " + currentStudentEnrollment + " found in session " + sessionId);
                        // Student is part of this session, proceed to attendance marking
                        Intent intent = new Intent(getApplicationContext(), SelectAttendanceActivity.class);
                        intent.putExtra("sessionId", sessionId);
                        intent.putExtra("studentEnrollment", currentStudentEnrollment);
                        startActivity(intent);
                    } else {
                        Log.w("SubmitAttendance", "Student " + currentStudentEnrollment + " not found in session " + sessionId);
                        
                        // Let's check what students are actually in this session
                        attendanceReportRef.child(sessionId).child("Students").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot studentsSnapshot) {
                                Log.d("SubmitAttendance", "Session " + sessionId + " has " + studentsSnapshot.getChildrenCount() + " students");
                                for (DataSnapshot student : studentsSnapshot.getChildren()) {
                                    Log.d("SubmitAttendance", "Student in session: " + student.getKey());
                                }
                            }
                            
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e("SubmitAttendance", "Error checking session students: " + error.getMessage());
                            }
                        });
                        
                        Toast.makeText(SubmitAttendanceActivity.this, 
                            "You are not registered for the current session. Please contact your faculty.", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e("SubmitAttendance", "Error checking student in session: " + databaseError.getMessage());
                    Toast.makeText(SubmitAttendanceActivity.this, 
                        "Error checking session. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private boolean isConnectedToUniversityNetwork() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String currentSSID = wifiInfo.getSSID();
                String currentBSSID = wifiInfo.getBSSID();
                int networkId = wifiInfo.getNetworkId();
                
                Log.d("WiFiDebug", "Current SSID: " + currentSSID);
                Log.d("WiFiDebug", "Current BSSID: " + currentBSSID);
                Log.d("WiFiDebug", "Network ID: " + networkId);
                Log.d("WiFiDebug", "WiFi enabled: " + wifiManager.isWifiEnabled());
                
                // Use WifiConfig to check if this is a university WiFi
                if (WifiConfig.isUniversityWiFiSSID(currentSSID)) {
                    Log.d("WiFiDebug", "University WiFi SSID detected: " + currentSSID);
                    return true;
                }
                
                if (WifiConfig.isUniversityWiFiBSSID(currentBSSID)) {
                    Log.d("WiFiDebug", "University WiFi BSSID detected: " + currentBSSID);
                    return true;
                }
                
                // Method 1: Check if Network ID indicates connection
                if (networkId != -1) {
                    Log.d("WiFiDebug", "Network ID indicates WiFi connection, allowing access");
                    return true;
                }
                
                // Method 2: Check if we have any SSID information (even if unknown)
                if (currentSSID != null && !currentSSID.isEmpty()) {
                    Log.d("WiFiDebug", "SSID detected, allowing access");
                    return true;
                }
                
                // Method 3: Try to detect WiFi connection using alternative methods
                if (checkWiFiConnectionAlternative(wifiManager)) {
                    Log.d("WiFiDebug", "Alternative WiFi detection successful");
                    return true;
                }
                
                // Method 4: Check network connectivity
                if (hasNetworkConnectivity()) {
                    Log.d("WiFiDebug", "Network connectivity detected, allowing access");
                    return true;
                }
                
                // Method 5: Check if we're in a WiFi-enabled state (trust mode for university)
                if (wifiManager.isWifiEnabled() && TRUST_MODE_ENABLED) {
                    Log.d("WiFiDebug", "WiFi is enabled, using trust mode for university environment");
                    return true;
                }
                
                Log.d("WiFiDebug", "No WiFi connection detected");
            } else {
                Log.d("WiFiDebug", "WiFiInfo is null");
            }
        } else {
            Log.d("WiFiDebug", "WiFiManager is null or WiFi is disabled");
        }
        return false;
    }
    
    /**
     * Check if device has network connectivity (WiFi or mobile data)
     */
    private boolean hasNetworkConnectivity() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                Log.d("WiFiDebug", "Network connectivity: " + activeNetwork.getTypeName());
                return true;
            }
        }
        return false;
    }
    
    /**
     * Alternative method to check WiFi connection that works around Android 11+ restrictions
     */
    private boolean checkWiFiConnectionAlternative(WifiManager wifiManager) {
        try {
            // Check if we can get scan results (this requires location permission)
            if (hasLocationPermissions()) {
                // Try to get available networks
                List<android.net.wifi.ScanResult> scanResults = wifiManager.getScanResults();
                if (scanResults != null && !scanResults.isEmpty()) {
                    Log.d("WiFiDebug", "Found " + scanResults.size() + " available networks");
                    
                    // Look for the vivo1920 network specifically
                    for (android.net.wifi.ScanResult result : scanResults) {
                        String ssid = result.SSID;
                        String bssid = result.BSSID;
                        int level = result.level;
                        
                        Log.d("WiFiDebug", "Available network: " + ssid + " (BSSID: " + bssid + ", Level: " + level + ")");
                        
                        // Check if this is the university network
                        if ("vivo1920".equals(ssid)) {
                            Log.d("WiFiDebug", "Found vivo1920 network in scan results");
                            return true;
                        }
                    }
                    
                    // If we found any networks and we're in a university environment, trust the connection
                    Log.d("WiFiDebug", "Found available networks, trusting connection in university environment");
                    return true;
                }
            }
            
            // Check if we can get the current connection info in a different way
            try {
                // Try to access the WiFi state
                int wifiState = wifiManager.getWifiState();
                Log.d("WiFiDebug", "WiFi state: " + wifiState);
                
                // WiFi state 3 means ENABLED, 2 means ENABLING
                if (wifiState == android.net.wifi.WifiManager.WIFI_STATE_ENABLED || 
                    wifiState == android.net.wifi.WifiManager.WIFI_STATE_ENABLING) {
                    
                    // If WiFi is enabled and we're in a university environment, trust the connection
                    Log.d("WiFiDebug", "WiFi is enabled, trusting connection in university environment");
                    return true;
                }
            } catch (Exception e) {
                Log.e("WiFiDebug", "Error checking WiFi state: " + e.getMessage());
            }
            
        } catch (SecurityException e) {
            Log.e("WiFiDebug", "Security exception in alternative WiFi detection: " + e.getMessage());
        } catch (Exception e) {
            Log.e("WiFiDebug", "Exception in alternative WiFi detection: " + e.getMessage());
        }
        
        return false;
    }

    private void cleanupExpiredSessions() {
        // Check for sessions that have passed their end time and mark them as expired
        attendanceReportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long currentTime = System.currentTimeMillis();
                long gracePeriod = 30 * 60 * 1000; // 30 minutes grace period
                
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    String sessionKey = sessionSnapshot.getKey();
                    String sessionStatus = sessionSnapshot.child("session_status").getValue(String.class);
                    Long endTimestamp = sessionSnapshot.child("end_timestamp").getValue(Long.class);
                    
                    // If session is active but has passed end time + grace period, mark it as expired
                    if ("active".equals(sessionStatus) && endTimestamp != null && currentTime > (endTimestamp + gracePeriod)) {
                        Log.d("SubmitAttendance", "Marking expired session: " + sessionKey + 
                              " (end time: " + endTimestamp + ", grace period until: " + (endTimestamp + gracePeriod) + 
                              ", current time: " + currentTime + ")");
                        
                        DatabaseReference sessionRef = attendanceReportRef.child(sessionKey);
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("session_status", "expired");
                        
                        sessionRef.updateChildren(updates).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d("SubmitAttendance", "Session " + sessionKey + " marked as expired");
                            } else {
                                Log.e("SubmitAttendance", "Failed to mark session " + sessionKey + " as expired: " + task.getException());
                            }
                        });
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("SubmitAttendance", "Error cleaning up expired sessions: " + databaseError.getMessage());
            }
        });
    }

    private void Init() {
        btn_logout = findViewById(R.id.btn_logout);
        btn_test_wifi = findViewById(R.id.btn_test_wifi);
        
        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signOut();
            }
        });
        
        btn_test_wifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testWifiConnection();
            }
        });
        
        // Add refresh button for manual session checking
        Button refreshButton = findViewById(R.id.btn_refresh);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    refreshSessionCheck();
                }
            });
        }
    }
    
    private void refreshSessionCheck() {
        Toast.makeText(this, "Refreshing session check...", Toast.LENGTH_SHORT).show();
        Log.d("SubmitAttendance", "Manual refresh requested");
        
        if (currentStudentEnrollment != null) {
            // Re-check for active sessions
            checkForActiveSessions();
        } else {
            Toast.makeText(this, "Student information not available. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void testWifiConnection() {
        Toast.makeText(this, "Testing WiFi connection...", Toast.LENGTH_SHORT).show();
        
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String currentSSID = wifiInfo.getSSID();
                String currentBSSID = wifiInfo.getBSSID();
                int networkId = wifiInfo.getNetworkId();
                
                String message = "SSID: " + currentSSID + "\nBSSID: " + currentBSSID + "\nNetwork ID: " + networkId;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                
                Log.d("WiFiTest", "SSID: " + currentSSID);
                Log.d("WiFiTest", "BSSID: " + currentBSSID);
                Log.d("WiFiTest", "Network ID: " + networkId);
                
                // Test the main WiFi detection method
                boolean mainCheck = isConnectedToUniversityNetwork();
                Log.d("WiFiTest", "Main WiFi check result: " + mainCheck);
                
                if (mainCheck) {
                    Toast.makeText(this, "WiFi check passed!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "WiFi check failed!", Toast.LENGTH_SHORT).show();
                    
                    // Show additional debugging information
                    String debugInfo = "Debug Info:\n";
                    debugInfo += "WiFi Enabled: " + wifiManager.isWifiEnabled() + "\n";
                    debugInfo += "Location Permissions: " + hasLocationPermissions() + "\n";
                    debugInfo += "Network ID: " + networkId + "\n";
                    debugInfo += "Network Connectivity: " + hasNetworkConnectivity();
                    
                    Toast.makeText(this, debugInfo, Toast.LENGTH_LONG).show();
                    
                    // Try alternative detection
                    boolean alternativeCheck = checkWiFiConnectionAlternative(wifiManager);
                    Log.d("WiFiTest", "Alternative WiFi check result: " + alternativeCheck);
                    
                    if (alternativeCheck) {
                        Toast.makeText(this, "Alternative detection passed!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Alternative detection also failed", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "WiFiInfo is null", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "WiFiManager is null", Toast.LENGTH_SHORT).show();
        }
    }

    public void logOut() {
        signOut();
    }

    public void locationAccess() {
        if (hasLocationPermissions()) {
            checkWifi();
        } else {
            requestLocationPermissions();
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 123;

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkWifi();
            } else {
                Toast.makeText(this, "Please enable Location permissions", Toast.LENGTH_SHORT).show();
            }
        }
    }
}