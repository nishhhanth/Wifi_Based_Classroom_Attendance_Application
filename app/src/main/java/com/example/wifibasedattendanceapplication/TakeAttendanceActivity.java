package com.example.wifibasedattendanceapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class TakeAttendanceActivity extends BaseAuthenticatedActivity {
    Spinner branchSpinner,divisionSpinner,subjectSpinner;
    Button btn_startAttendanceSession;
    String[] branchArray,divisionArray, subjectArray;
    private static final String PREFS_NAME = "attendance_prefs";
    private static final String KEY_ACTIVE_SESSION_ID = "active_session_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_attendance);

        // Only check authentication when needed for database operations
        // Don't check immediately on create

        Init();
        Buttons();

        // Prompt to resume or end any active session
        checkAndPromptResume();
    }

    private void Buttons() {
        btn_startAttendanceSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSelectionValid()) {

                    String selectedBranch = branchSpinner.getSelectedItem().toString();
                    String selectedDivision = divisionSpinner.getSelectedItem().toString();
                    String selectedSubject = subjectSpinner.getSelectedItem().toString();

                    Intent intent = new Intent(getApplicationContext(), activity_session.class);

                    intent.putExtra("branch", selectedBranch);
                    intent.putExtra("division", selectedDivision);
                    intent.putExtra("subject", selectedSubject);

                    startActivity(intent);
                } else {
                    Toast.makeText(TakeAttendanceActivity.this, "Please select all three options", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean isSelectionValid() {
        return (branchSpinner.getSelectedItemPosition() > 0) &&
                (subjectSpinner.getSelectedItemPosition() > 0) &&
                (divisionSpinner.getSelectedItemPosition() > 0);
    }

    private void Init() {
        branchSpinner = findViewById(R.id.branchSpinner);
        divisionSpinner = findViewById(R.id.divisionSpinner);
        subjectSpinner = findViewById(R.id.subjectSpinner);

        btn_startAttendanceSession = findViewById(R.id.btn_startAttendanceSession);
        
        branchArray = getResources().getStringArray(R.array.branch_array);
        ArrayAdapter<String> branchArrayAdapter = new ArrayAdapter<String>(this, R.layout.spinner_list, branchArray);
        branchArrayAdapter.setDropDownViewResource(R.layout.spinner_list);
        branchSpinner.setAdapter(branchArrayAdapter);
        branchSpinner.setSelection(0, false);

        divisionArray = getResources().getStringArray(R.array.division_array);
        ArrayAdapter<String> divisionArrayAdapter = new ArrayAdapter<String>(this, R.layout.spinner_list, divisionArray);
        divisionArrayAdapter.setDropDownViewResource(R.layout.spinner_list);
        divisionSpinner.setAdapter(divisionArrayAdapter);
        divisionSpinner.setSelection(0, false);

        // group spinner removed

        subjectArray = getResources().getStringArray(R.array.subject_array);
        ArrayAdapter<String> subjectArrayAdapter = new ArrayAdapter<String>(this, R.layout.spinner_list, subjectArray);
        subjectArrayAdapter.setDropDownViewResource(R.layout.spinner_list);
        subjectSpinner.setAdapter(subjectArrayAdapter);
        subjectSpinner.setSelection(0, false);
    }

    private void checkAndPromptResume() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedSessionId = prefs.getString(KEY_ACTIVE_SESSION_ID, null);
        if (savedSessionId == null || savedSessionId.trim().isEmpty()) {
            return;
        }

        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(savedSessionId);

        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Clear stale id
                    prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply();
                    return;
                }
                String status = snapshot.child("session_status").getValue(String.class);
                if ("active".equals(status)) {
                    new AlertDialog.Builder(TakeAttendanceActivity.this)
                            .setTitle("Active session in progress")
                            .setMessage("You have an active attendance session. Do you want to resume or end it?")
                            .setPositiveButton("Resume", (d, w) -> {
                                Intent i = new Intent(getApplicationContext(), activity_session.class);
                                i.putExtra("resumeSessionId", savedSessionId);
                                startActivity(i);
                            })
                            .setNegativeButton("End Session", (d, w) -> {
                                // End now
                                endExistingSession(savedSessionId);
                            })
                            .setCancelable(true)
                            .show();
                } else if ("ended".equals(status) || "expired".equals(status)) {
                    // Clear if not active
                    prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void endExistingSession(@NonNull String sessionId) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceReport")
                .child(sessionId);

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("session_status", "ended");
        updates.put("end_timestamp", System.currentTimeMillis());

        sessionRef.updateChildren(updates).addOnCompleteListener(task -> {
            // Clear persisted session regardless of success to avoid loops
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_ACTIVE_SESSION_ID).apply();
            if (task.isSuccessful()) {
                Toast.makeText(this, "Session ended.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to end session. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

}