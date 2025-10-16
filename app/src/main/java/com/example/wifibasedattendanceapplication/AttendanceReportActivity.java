package com.example.wifibasedattendanceapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class AttendanceReportActivity extends BaseAuthenticatedActivity {

    private static final String TAG = "AttendanceReport";
    private ProgressDialog progressDialog;
    private FirebaseDatabase firebaseDatabase;

    private TextView text_absent_number, text_present_number, text_students_number;
    private Button downloadBtn;

    private String sessionId;

    // Live update listeners
    private DatabaseReference sessionStudentsRef;
    private ValueEventListener sessionStudentsListener;
    private DatabaseReference studentsRootRef;
    private ValueEventListener studentsAttendanceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_report);

        // Only check authentication when needed for database operations
        // Don't check immediately on create

        // Views
        downloadBtn = findViewById(R.id.download);
        text_absent_number = findViewById(R.id.text_absent_number);
        text_present_number = findViewById(R.id.text_present_number);
        text_students_number = findViewById(R.id.text_students_number);

        // Firebase
        firebaseDatabase = FirebaseDatabase.getInstance();

        // Progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Creating Excel File...");
        progressDialog.setCancelable(false);

        // Session id
        Intent intent = getIntent();
        sessionId = intent != null ? intent.getStringExtra("sessionId") : null;

        if (sessionId == null || sessionId.trim().isEmpty()) {
            Toast.makeText(this, "No session ID found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        performDatabaseOperation(() -> loadAttendanceStatistics());

        downloadBtn.setOnClickListener(v -> performDatabaseOperation(() -> exportExcelFile()));
        
        // Add refresh button for debugging
        Button refreshBtn = findViewById(R.id.btn_refresh_stats);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> performDatabaseOperation(() -> loadAttendanceStatistics()));
        }
        
        // Add finish button functionality: logout and clear back stack
        Button finishBtn = findViewById(R.id.btn_finish);
        if (finishBtn != null) {
            finishBtn.setOnClickListener(v -> {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                Intent i = new Intent(AttendanceReportActivity.this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        }

        // Auto-refresh when database changes
        setupAutoRefreshListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        teardownAutoRefreshListeners();
    }

    private void setupAutoRefreshListeners() {
        // Listen to session's Students node (counts/status in report)
        sessionStudentsRef = firebaseDatabase.getReference("AttendanceReport")
                .child(sessionId)
                .child("Students");
        sessionStudentsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                performDatabaseOperation(() -> loadAttendanceStatistics());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        sessionStudentsRef.addValueEventListener(sessionStudentsListener);

        // Listen to Students root so if any student's Attendance/{sessionId} changes, we refresh
        studentsRootRef = firebaseDatabase.getReference("Students");
        studentsAttendanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                performDatabaseOperation(() -> loadAttendanceStatistics());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        studentsRootRef.addValueEventListener(studentsAttendanceListener);
    }

    private void teardownAutoRefreshListeners() {
        if (sessionStudentsRef != null && sessionStudentsListener != null) {
            sessionStudentsRef.removeEventListener(sessionStudentsListener);
        }
        if (studentsRootRef != null && studentsAttendanceListener != null) {
            studentsRootRef.removeEventListener(studentsAttendanceListener);
        }
    }

    /**
     * Loads total/present/absent from:
     * AttendanceReport/{sessionId}/Students/{enrollment}/attendance_status
     * Only counts students who were actually included in the session
     */
    private void loadAttendanceStatistics() {
        Log.d(TAG, "Loading statistics for session: " + sessionId);
        
        // Check authentication before proceeding
        if (!isUserAuthenticated()) {
            Log.e(TAG, "User not authenticated, cannot load statistics");
            return;
        }

        // First, get the session details to find which students were included
        DatabaseReference sessionRef = firebaseDatabase.getReference("AttendanceReport").child(sessionId);
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot sessionSnap) {
                if (!sessionSnap.exists()) {
                    Log.e(TAG, "Session not found: " + sessionId);
                    Toast.makeText(AttendanceReportActivity.this, "Session not found!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Get the Students section from the session
                DataSnapshot studentsSnap = sessionSnap.child("Students");
                if (!studentsSnap.exists()) {
                    Log.w(TAG, "No students found in session: " + sessionId);
                    text_students_number.setText("0");
                    text_present_number.setText("0");
                    text_absent_number.setText("0");
                    return;
                }

                int total = (int) studentsSnap.getChildrenCount();
                int present = 0;
                int absent = 0;

                Log.d(TAG, "Session has " + total + " students");

                // Count attendance for students who were actually in this session
                final int[] presentCount = {0};
                final int[] absentCount = {0};
                final int[] processedCount = {0};
                
                for (DataSnapshot student : studentsSnap.getChildren()) {
                    String enrollment = student.getKey();
                    String sessionStatus = safeString(student.child("attendance_status").getValue());
                    
                    Log.d(TAG, "Student " + enrollment + " has session status: " + sessionStatus);
                    
                    // Also check the student's individual attendance record
                    DatabaseReference studentAttendanceRef = firebaseDatabase.getReference("Students")
                            .child(enrollment)
                            .child("Attendance")
                            .child(sessionId);
                    
                    studentAttendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot studentAttendanceSnap) {
                            String studentStatus = safeString(studentAttendanceSnap.getValue());
                            Log.d(TAG, "Student " + enrollment + " has individual status: " + studentStatus);
                            
                            // Determine final status - prioritize individual record over session record
                            String finalStatus = "A"; // default to absent
                            
                            if ("P".equalsIgnoreCase(studentStatus) || "Present".equalsIgnoreCase(studentStatus)) {
                                finalStatus = "Present";
                            } else if ("A".equalsIgnoreCase(studentStatus) || "Absent".equalsIgnoreCase(studentStatus)) {
                                finalStatus = "Absent";
                            } else if ("Present".equalsIgnoreCase(sessionStatus) || "P".equalsIgnoreCase(sessionStatus)) {
                                finalStatus = "Present";
                            } else if ("Not Marked".equalsIgnoreCase(sessionStatus) || sessionStatus.isEmpty()) {
                                finalStatus = "Absent";
                            }
                            
                            Log.d(TAG, "Student " + enrollment + " final status: " + finalStatus);
                            
                            // Update counts based on final status
                            if ("Present".equalsIgnoreCase(finalStatus)) {
                                presentCount[0]++;
                                Log.d(TAG, "Student " + enrollment + " counted as Present");
                            } else {
                                absentCount[0]++;
                                Log.d(TAG, "Student " + enrollment + " counted as Absent");
                            }
                            
                            processedCount[0]++;
                            
                            // Update UI after all students are processed
                            if (processedCount[0] == total) {
                                updateAttendanceUI(total, presentCount[0], absentCount[0]);
                            }
                        }
                        
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error getting student attendance for " + enrollment + ": " + error.getMessage());
                            // Fallback to session status
                            String finalStatus = "Present".equalsIgnoreCase(sessionStatus) ? "Present" : "Absent";
                            
                            if ("Present".equalsIgnoreCase(finalStatus)) {
                                presentCount[0]++;
                            } else {
                                absentCount[0]++;
                            }
                            
                            processedCount[0]++;
                            
                            // Update UI after all students are processed
                            if (processedCount[0] == total) {
                                updateAttendanceUI(total, presentCount[0], absentCount[0]);
                            }
                        }
                    });
                }
                
                // Don't update UI here - wait for all students to be processed
                // The updateAttendanceUI method will be called after each student is processed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "loadAttendanceStatistics cancelled: " + error.getMessage());
                handleDatabaseError(error);
            }
        });
    }

    /**
     * Export Excel:
     * 1) Writes session metadata from AttendanceReport/{sessionId} (excluding any students map)
     * 2) Adds header row
     * 3) Writes rows from:
     *      a) AttendanceReport/{sessionId}/students or Students (case-insensitive) if present
     *      b) Fallback to Students/{enrollment}/Attendance/{sessionId}
     * 4) Saves via MediaStore -> Downloads
     */
    private void exportExcelFile() {
        // Check authentication before proceeding
        if (!isUserAuthenticated()) {
            Log.e(TAG, "User not authenticated, cannot export file");
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressDialog.show();

        DatabaseReference rootRef = firebaseDatabase.getReference();
        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot rootSnap) {
                try {
                    if (!rootSnap.exists()) {
                        Toast.makeText(getApplicationContext(), "No data found", Toast.LENGTH_SHORT).show();
                        progressDialog.dismiss();
                        return;
                    }

                    DataSnapshot sessionSnap = rootSnap.child("AttendanceReport").child(sessionId);
                    if (!sessionSnap.exists()) {
                        Toast.makeText(getApplicationContext(), "Session not found", Toast.LENGTH_SHORT).show();
                        progressDialog.dismiss();
                        return;
                    }

                    Workbook workbook = new XSSFWorkbook();
                    Sheet sheet = workbook.createSheet("Attendance");
                    sheet.setDefaultColumnWidth(24);
                    int rowNum = 0;

                    // 1) Session metadata (ignore any students map)
                    for (DataSnapshot kv : sessionSnap.getChildren()) {
                        String key = kv.getKey();
                        if (key == null) continue;
                        String lower = key.toLowerCase(Locale.US);
                        if (lower.equals("students")) continue; // skip any students section

                        Row r = sheet.createRow(rowNum++);
                        r.createCell(0).setCellValue(key);
                        r.createCell(1).setCellValue(safeString(kv.getValue()));
                    }

                    // blank row
                    rowNum++;

                    // 2) Header
                    Row header = sheet.createRow(rowNum++);
                    header.createCell(0).setCellValue("Student Enrollment");
                    header.createCell(1).setCellValue("Student Name");
                    header.createCell(2).setCellValue("Student Email");
                    header.createCell(3).setCellValue("Division");
                    header.createCell(4).setCellValue("Attendance");

                    // 3) Try to read a session-level students map (case-insensitive key)
                    DataSnapshot sessionStudentsSnap = getFirstPresentChild(sessionSnap, "students", "Students");
                    DataSnapshot allStudentsSnap = rootSnap.child("Students");

                    if (sessionStudentsSnap != null && sessionStudentsSnap.exists()) {
                        // Use mapping from session -> studentId -> ("P"/"A" or attendance_status)
                        Log.d(TAG, "Exporting using session students map");
                        for (DataSnapshot studentEntry : sessionStudentsSnap.getChildren()) {
                            String enrollmentNo = studentEntry.getKey();
                            if (enrollmentNo == null) continue;

                            String status = null;
                            // support either direct value "P"/"A" or child "attendance_status"
                            if (studentEntry.getValue() instanceof String) {
                                status = (String) studentEntry.getValue();
                            } else {
                                status = safeString(studentEntry.child("attendance_status").getValue());
                            }
                            if (status.isEmpty()) status = "A"; // default

                            writeStudentRow(sheet, rowNum++, allStudentsSnap.child(enrollmentNo), enrollmentNo, status);

                            // Optional: back-write "A" to Students/{id}/Attendance/{sessionId} if not present
                            backwriteAbsentIfNeeded(enrollmentNo, sessionId, status);
                        }
                    } else {
                        // 3b) Fallback: derive from Students/{enrollment}/Attendance/{sessionId}
                        Log.d(TAG, "Exporting using Students fallback");
                        for (DataSnapshot student : allStudentsSnap.getChildren()) {
                            String enrollmentNo = student.getKey();
                            if (enrollmentNo == null) continue;

                            String status = safeString(student.child("Attendance").child(sessionId).getValue());
                            if (status.isEmpty()) status = "A"; // default if missing

                            writeStudentRow(sheet, rowNum++, student, enrollmentNo, status);

                            // Optional: persist default "A" to DB when missing
                            backwriteAbsentIfNeeded(enrollmentNo, sessionId, status);
                        }
                    }

                    // 4) Save to Downloads via MediaStore
                    String fileName = "AttendanceReport_" +
                            new SimpleDateFormat("dd-MM-yyyy_HH-mm", Locale.US).format(new Date()) +
                            ".xlsx";

                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");

                    Uri uri = getContentResolver()
                            .insert(MediaStore.Files.getContentUri("external"), values);

                    if (uri == null) {
                        workbook.close();
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Error creating file", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        workbook.write(os);
                    }
                    workbook.close();

                    progressDialog.dismiss();
                    Toast.makeText(getApplicationContext(), "Excel saved to Downloads", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    Log.e(TAG, "Export error", e);
                    progressDialog.dismiss();
                    Toast.makeText(getApplicationContext(), "Error creating Excel file", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                handleDatabaseError(error);
            }
        });
    }

    private void writeStudentRow(Sheet sheet, int rowNum, DataSnapshot studentSnap, String enrollmentNo, String status) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(enrollmentNo);

        String name = safeString(studentSnap.child("student_name").getValue());
        String email = safeString(studentSnap.child("student_email").getValue());
        String division = safeString(studentSnap.child("Division").getValue());

        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(email);
        row.createCell(3).setCellValue(division);
        row.createCell(4).setCellValue(status);
    }

    /**
     * Back-write "A" if status is missing/not stored.
     * Keeps your DB consistent for future exports and stats screens.
     */
    private void backwriteAbsentIfNeeded(String enrollmentNo, String sessionId, String status) {
        if (!"A".equalsIgnoreCase(status)) return; // only backwrite for Absent defaults

        DatabaseReference ref = firebaseDatabase.getReference("Students")
                .child(enrollmentNo)
                .child("Attendance")
                .child(sessionId);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    ref.setValue("A").addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Back-wrote Absent for " + enrollmentNo);
                            }
                        }
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    /**
     * Fixes missing students in the session by adding students from the same division and group
     */
    private void fixMissingStudents() {
        Log.d(TAG, "Fixing missing students for session: " + sessionId);
        
        // First, get the session details to find division and group
        DatabaseReference sessionRef = firebaseDatabase.getReference("AttendanceReport").child(sessionId);
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot sessionSnap) {
                if (!sessionSnap.exists()) {
                    Log.e(TAG, "Session not found: " + sessionId);
                    Toast.makeText(AttendanceReportActivity.this, "Session not found!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String sessionDivision = safeString(sessionSnap.child("division").getValue());
                Log.d(TAG, "Session has Division: " + sessionDivision);
                
                if (sessionDivision.isEmpty()) {
                    Log.e(TAG, "Session missing division information");
                    Toast.makeText(AttendanceReportActivity.this, "Session missing division information!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Get all students and add missing ones
                DatabaseReference studentsRef = firebaseDatabase.getReference("Students");
                studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot studentsSnap) {
                        int addedCount = 0;
                        
                        for (DataSnapshot student : studentsSnap.getChildren()) {
                            String enrollment = student.getKey();
                            String studentDivision = safeString(student.child("Division").getValue());
                            
                            // Check if student belongs to this session's division
                            if (sessionDivision.equals(studentDivision)) {
                                // Check if student is already in the session
                                DataSnapshot sessionStudentsSnap = sessionSnap.child("Students");
                                if (!sessionStudentsSnap.child(enrollment).exists()) {
                                    // Add missing student to session
                                    Log.d(TAG, "Adding missing student: " + enrollment);
                                    
                                    DatabaseReference sessionStudentRef = firebaseDatabase.getReference("AttendanceReport")
                                            .child(sessionId)
                                            .child("Students")
                                            .child(enrollment);
                                    
                                    // Initialize session attendance as "Not Marked"
                                    sessionStudentRef.child("attendance_status").setValue("Not Marked")
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                Log.d(TAG, "Added student " + enrollment + " to session");
                                            } else {
                                                Log.e(TAG, "Failed to add student " + enrollment + ": " + task.getException());
                                            }
                                        });
                                    
                                    // Initialize student's attendance record as "A" (absent)
                                    FirebaseDatabase.getInstance().getReference("Students")
                                            .child(enrollment)
                                            .child("Attendance")
                                            .child(sessionId)
                                            .setValue("A")
                                            .addOnCompleteListener(task -> {
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "Initialized attendance record for " + enrollment);
                                                } else {
                                                    Log.e(TAG, "Failed to initialize attendance for " + enrollment + ": " + task.getException());
                                                }
                                            });
                                    
                                    addedCount++;
                                }
                            }
                        }
                        
                        if (addedCount > 0) {
                            Toast.makeText(AttendanceReportActivity.this, 
                                "Added " + addedCount + " missing students to session", Toast.LENGTH_SHORT).show();
                            // Reload statistics after adding students
                            loadAttendanceStatistics();
                        } else {
                            Toast.makeText(AttendanceReportActivity.this, 
                                "No missing students found", Toast.LENGTH_SHORT).show();
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error fixing missing students: " + error.getMessage());
                        Toast.makeText(AttendanceReportActivity.this, 
                            "Error fixing students: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error getting session details: " + error.getMessage());
                Toast.makeText(AttendanceReportActivity.this, 
                    "Error getting session details: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String safeString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * Returns the first non-null child snapshot for any of the provided keys (case-insensitive)
     */
    private DataSnapshot getFirstPresentChild(DataSnapshot parent, String... possibleKeys) {
        if (parent == null) return null;
        for (String k : possibleKeys) {
            if (k == null) continue;
            // try exact
            DataSnapshot exact = parent.child(k);
            if (exact.exists()) return exact;
            // try case-insensitive search
            for (DataSnapshot child : parent.getChildren()) {
                if (child.getKey() != null && child.getKey().equalsIgnoreCase(k)) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Updates the attendance UI with the final counts
     */
    private void updateAttendanceUI(int total, int present, int absent) {
        Log.d(TAG, "Final Stats -> total: " + total + " present: " + present + " absent: " + absent);
        
        runOnUiThread(() -> {
            text_students_number.setText(String.valueOf(total));
            text_present_number.setText(String.valueOf(present));
            text_absent_number.setText(String.valueOf(absent));
        });
    }
}
