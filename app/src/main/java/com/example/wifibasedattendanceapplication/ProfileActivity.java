package com.example.wifibasedattendanceapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends BaseAuthenticatedActivity {

    private static final String TAG = "ProfileActivity";

    private TextView tvAcademicYear;
    private TextView tvStudentEmail;
    private TextView tvName;
    private TextView tvClassRoll;
    private ImageView btnBack;
    private TextView btnDone;

    private DatabaseReference studentsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        initViews();
        setupClicks();
        loadProfile();
    }

    private void initViews() {
        btnBack = findViewById(R.id.iv_back);
        btnDone = findViewById(R.id.tv_done);

        tvAcademicYear = findViewById(R.id.tv_academic_year_value);
        tvStudentEmail = findViewById(R.id.tv_student_email_value);
        tvName = findViewById(R.id.tv_name);
        tvClassRoll = findViewById(R.id.tv_class_roll);
    }

    private void setupClicks() {
        View.OnClickListener close = v -> finish();
        if (btnBack != null) btnBack.setOnClickListener(close);
        if (btnDone != null) btnDone.setOnClickListener(close);
    }

    private void loadProfile() {
        String email = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;

        if (email == null) {
            Log.w(TAG, "No authenticated user; showing placeholders");
            bindPlaceholders();
            return;
        }

        studentsRef.orderByChild("student_email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.w(TAG, "Student not found for email: " + email);
                            bindPlaceholders();
                            return;
                        }

                        // Take the first matched student
                        for (DataSnapshot child : snapshot.getChildren()) {
                            bindFromSnapshot(child);
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "loadProfile cancelled: " + error.getMessage());
                        bindPlaceholders();
                    }
                });
    }

    private void bindFromSnapshot(DataSnapshot s) {
        String name = getStringValue(s, "student_name", "Student");
        String division = getStringValue(s, "Division", null);
        String rollNo = getStringValue(s, "roll_no", null);
        tvName.setText(name);
        String classRoll;
        if (division != null || rollNo != null) {
            String classPart = (division != null ? "Class " + division : null);
            String rollPart = (rollNo != null ? "Roll no: " + rollNo : null);
            classRoll = joinWithSeparator(" | ", classPart, rollPart);
        } else {
            classRoll = "Class";
        }
        tvClassRoll.setText(classRoll);
        tvAcademicYear.setText(getStringValue(s, "academic_year", "2024-2025"));
        tvStudentEmail.setText(getStringValue(s, "student_email", ""));
    }

    private void bindPlaceholders() {
        tvAcademicYear.setText("2024-2025");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            tvStudentEmail.setText(FirebaseAuth.getInstance().getCurrentUser().getEmail());
        } else {
            tvStudentEmail.setText("");
        }
        tvName.setText("Student");
        tvClassRoll.setText("Class");
    }

    private String getStringValue(DataSnapshot s, String key, String fallback) {
        String v = s.child(key).getValue(String.class);
        return v != null ? v : fallback;
    }

    private String joinWithSeparator(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }
}


