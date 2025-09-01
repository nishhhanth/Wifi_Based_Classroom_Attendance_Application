package com.example.wifibasedattendanceapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginStudentActivity extends AppCompatActivity implements TextWatcher {

    private TextInputLayout emailInputLayout, passwordInputLayout;
    private String loginEmail;
    private FirebaseAuth mAuth;
    ProgressDialog progressDialog;
    private DatabaseReference studentsRef;
    private String enrollmentNo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_student);

        // Don't check auth state here as this is a login activity
        init();
    }


    private void init() {
        Button loginButton = findViewById(R.id.btn_login);
        emailInputLayout = findViewById(R.id.edt_loginEmail);
        passwordInputLayout = findViewById(R.id.edt_loginPassword);

        emailInputLayout.getEditText().addTextChangedListener(this);
        passwordInputLayout.getEditText().addTextChangedListener(this);

        mAuth = FirebaseAuth.getInstance();
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Test database connection and paths
        Log.d("LoginStudent", "Testing database connection...");
        testDatabasePaths();
        
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("LoginStudent", "Database connection test successful. Found " + dataSnapshot.getChildrenCount() + " students.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LoginStudent", "Database connection test failed: " + error.getMessage());
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                login();
            }
        });
    }

    private void login() {
        loginEmail = emailInputLayout.getEditText().getText().toString();
        String loginPassword = passwordInputLayout.getEditText().getText().toString();

        Log.d("LoginStudent", "Login attempt - Email: '" + loginEmail + "'");

        if (loginEmail.isEmpty()) {
            emailInputLayout.setError("Email Cannot be Empty!");
            return;
        } else if (loginPassword.isEmpty()) {
            passwordInputLayout.setError("Password Cannot be Empty!");
            return;
        } else if (!isValidEmail(loginEmail)) {
            emailInputLayout.setError("Invalid Email or not from 'gmail.com' domain");
            return;
        }

        showProgressDialog("Logging in...");

        mAuth.signInWithEmailAndPassword(loginEmail, loginPassword)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d("Debug","Firebase Authentication successful for email: '" + loginEmail + "'");
                            searchEnrollment();
                        } else {
                            Log.e("LoginStudent", "Firebase Authentication failed: " + task.getException().getMessage());
                            Toast.makeText(getApplicationContext(), "Authentication failed.", Toast.LENGTH_SHORT).show();
                            closeProgressDialog();
                        }
                    }
                });
    }


    private void searchEnrollment() {
        // Ensure user is authenticated before searching
        if (mAuth.getCurrentUser() == null) {
            Log.e("LoginStudent", "User not authenticated after login");
            closeProgressDialog();
            return;
        }
        
        // Refresh token before database operation
        mAuth.getCurrentUser().getIdToken(true)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    searchEnrollmentWithRetry(loginEmail, 0);
                } else {
                    Log.e("LoginStudent", "Token refresh failed");
                    closeProgressDialog();
                    Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });
    }
    private void searchEnrollmentWithRetry(String email, int retryCount) {
        if (retryCount > 3) {
            Toast.makeText(getApplicationContext(), "Email not found after multiple attempts. Please try again.", Toast.LENGTH_LONG).show();
            closeProgressDialog();
            return;
        }

        Log.d("LoginStudent", "Searching for email: '" + email + "' in database. Attempt: " + (retryCount + 1));
        Log.d("LoginStudent", "Database reference path: " + studentsRef.toString());

        // First, let's test if we can read the database at all
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("LoginStudent", "Database query completed. Total children: " + dataSnapshot.getChildrenCount());
                
                // Log the entire database structure for debugging
                Log.d("LoginStudent", "=== DATABASE STRUCTURE DEBUG ===");
                for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                    String studentKey = studentSnapshot.getKey();
                    Log.d("LoginStudent", "Student Key: " + studentKey);
                    
                    // Log all fields for this student
                    for (DataSnapshot fieldSnapshot : studentSnapshot.getChildren()) {
                        String fieldKey = fieldSnapshot.getKey();
                        Object fieldValue = fieldSnapshot.getValue();
                        Log.d("LoginStudent", "  - " + fieldKey + ": " + fieldValue);
                    }
                    Log.d("LoginStudent", "---");
                }
                Log.d("LoginStudent", "=== END DATABASE STRUCTURE ===");
                
                boolean found = false;
                for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                    String studentKey = studentSnapshot.getKey();
                    String studentEmail = studentSnapshot.child("student_email").getValue(String.class);
                    
                    Log.d("LoginStudent", "Checking student: " + studentKey + " with email: '" + studentEmail + "'");
                    
                    if (studentEmail != null && studentEmail.equals(email)) {
                        Log.d("LoginStudent", "MATCH FOUND! Student: " + studentKey + " with email: '" + studentEmail + "'");
                        enrollmentNo = studentSnapshot.getKey();
                        String hardwareAddress = fetchUniqueHardwareID();
                        storeMacAddressInFirebase(enrollmentNo, hardwareAddress);
                        Log.d("Debug","uniqueID: "+hardwareAddress);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Log.e("LoginStudent", "Email '" + email + "' NOT FOUND in database after checking all students");
                    Log.e("LoginStudent", "Available student emails in database:");
                    
                    // Log all available emails for debugging
                    for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                        String studentKey = studentSnapshot.getKey();
                        String studentEmail = studentSnapshot.child("student_email").getValue(String.class);
                        Log.e("LoginStudent", "  - " + studentKey + ": '" + studentEmail + "'");
                    }
                    
                    // Email not found, retry after delay
                    Log.d("LoginStudent", "Email not found, retrying in 2 seconds... Attempt: " + (retryCount + 1));
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            searchEnrollmentWithRetry(email, retryCount + 1);
                        }
                    }, 2000); // 2 second delay
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                closeProgressDialog();
                Log.e("LoginStudent", "Database error: " + error.getMessage());
                Log.e("LoginStudent", "Database error code: " + error.getCode());
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Toast.makeText(LoginStudentActivity.this, "Authentication required. Please login again.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginStudentActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String fetchUniqueHardwareID() {
//        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//        if (manager != null) {
//            WifiInfo info = manager.getConnectionInfo();
//            return info.getMacAddress();
//        }
//        return null;
        return Secure.getString(getContentResolver(), Secure.ANDROID_ID);
    }

    private void storeMacAddressInFirebase(String enrollmentNo, String macAddress) {
        DatabaseReference enrollmentRef = studentsRef.child(enrollmentNo);

        enrollmentRef.child("hardware_id").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String existingHardwareId = dataSnapshot.getValue(String.class);
                if (existingHardwareId == null||existingHardwareId.isEmpty()) {
                    enrollmentRef.child("hardware_id").setValue(macAddress);
                    startActivity(new Intent(getApplicationContext(), SubmitAttendanceActivity.class));
                    Toast.makeText(getApplicationContext(), "Login successful!", Toast.LENGTH_SHORT).show();
                    closeProgressDialog();
                }
                else if (macAddress.equals(existingHardwareId)) {
                    // Same device login - allow login
                    startActivity(new Intent(getApplicationContext(), SubmitAttendanceActivity.class));
                    Toast.makeText(getApplicationContext(), "Login successful!", Toast.LENGTH_SHORT).show();
                    closeProgressDialog();
                }
                else{
                    startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(LoginStudentActivity.this, "Cannot login on another device!", Toast.LENGTH_SHORT).show();
                    closeProgressDialog();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }


    private void showProgressDialog(String text) {
        progressDialog = new ProgressDialog(LoginStudentActivity.this);
        progressDialog.setMessage(text);
        progressDialog.show();
    }

    private void closeProgressDialog() {
        progressDialog.dismiss();
    }
    private boolean isValidEmail(String email) {
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            String domain = email.substring(email.indexOf('@') + 1);
            return domain.equals("gmail.com");
        }
        return false;
    }

    // Test different database paths to debug the issue
    private void testDatabasePaths() {
        Log.d("LoginStudent", "=== TESTING DIFFERENT DATABASE PATHS ===");
        
        // Test root path
        FirebaseDatabase.getInstance().getReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("LoginStudent", "Root database contains: " + dataSnapshot.getChildrenCount() + " top-level nodes");
                for (DataSnapshot node : dataSnapshot.getChildren()) {
                    Log.d("LoginStudent", "Root node: " + node.getKey());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LoginStudent", "Root database test failed: " + error.getMessage());
            }
        });
        
        // Test Students path specifically
        FirebaseDatabase.getInstance().getReference("Students").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("LoginStudent", "Students path contains: " + dataSnapshot.getChildrenCount() + " students");
                if (dataSnapshot.exists()) {
                    Log.d("LoginStudent", "Students node exists and is accessible");
                } else {
                    Log.e("LoginStudent", "Students node does not exist or is not accessible");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LoginStudent", "Students path test failed: " + error.getMessage());
            }
        });
        
        Log.d("LoginStudent", "=== END DATABASE PATH TESTING ===");
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        clearErrors();
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        clearErrors();
    }

    @Override
    public void afterTextChanged(Editable editable) {
        clearErrors();
    }

    private void clearErrors() {
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
    }
}
