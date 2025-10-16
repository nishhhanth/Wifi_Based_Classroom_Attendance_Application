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

/**
 * Displays the student's profile by reading from Firebase Realtime Database.
 * The activity looks up the current user's email and queries the `Students` node
 * by `student_email` to load the record. All fields render with safe fallbacks
 * so the screen is always populated even if some values are missing.
 */
public class ProfileActivity extends BaseAuthenticatedActivity {

    private static final String TAG = "ProfileActivity";

    private TextView tvName;
    private TextView tvClassRoll;
    private TextView tvAadhar;
    private TextView tvAcademicYear;
    private TextView tvAdmissionClass;
    private TextView tvOldAdmissionNo;
    private TextView tvDateOfAdmission;
    private TextView tvDateOfBirth;
    private TextView tvParentMailId;
    private TextView tvMotherName;
    private TextView tvFatherName;
    private TextView tvPermanentAddress;
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

        tvName = findViewById(R.id.tv_name);
        tvClassRoll = findViewById(R.id.tv_class_roll);
        tvAadhar = findViewById(R.id.tv_aadhar);
        tvAcademicYear = findViewById(R.id.tv_academic_year_value);
        tvAdmissionClass = findViewById(R.id.tv_admission_class_value);
        tvOldAdmissionNo = findViewById(R.id.tv_old_admission_no_value);
        tvDateOfAdmission = findViewById(R.id.tv_date_of_admission_value);
        tvDateOfBirth = findViewById(R.id.tv_date_of_birth_value);
        tvParentMailId = findViewById(R.id.tv_parent_mail_id_value);
        tvMotherName = findViewById(R.id.tv_mother_name_value);
        tvFatherName = findViewById(R.id.tv_father_name_value);
        tvPermanentAddress = findViewById(R.id.tv_permanent_address_value);
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

        tvAadhar.setText(getStringValue(s, "aadhar_no", ""));
        tvAcademicYear.setText(getStringValue(s, "academic_year", "2024-2025"));
        tvAdmissionClass.setText(getStringValue(s, "admission_class", ""));
        tvOldAdmissionNo.setText(getStringValue(s, "old_admission_no", ""));
        tvDateOfAdmission.setText(getStringValue(s, "date_of_admission", ""));
        tvDateOfBirth.setText(getStringValue(s, "date_of_birth", ""));
        tvParentMailId.setText(getStringValue(s, "parent_email", getStringValue(s, "student_email", "")));
        tvMotherName.setText(getStringValue(s, "mother_name", ""));
        tvFatherName.setText(getStringValue(s, "father_name", ""));
        tvPermanentAddress.setText(getStringValue(s, "permanent_address", ""));
    }

    private void bindPlaceholders() {
        tvName.setText("Student");
        tvClassRoll.setText("Class");
        tvAadhar.setText("");
        tvAcademicYear.setText("2024-2025");
        tvAdmissionClass.setText("");
        tvOldAdmissionNo.setText("");
        tvDateOfAdmission.setText("");
        tvDateOfBirth.setText("");
        tvParentMailId.setText("");
        tvMotherName.setText("");
        tvFatherName.setText("");
        tvPermanentAddress.setText("");
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


