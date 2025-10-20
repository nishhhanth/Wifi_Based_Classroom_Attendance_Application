package com.example.wifibasedattendanceapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

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

public class LoginFacultyActivity extends BaseAuthenticatedActivity implements TextWatcher {

    private Button loginButton;
    private ProgressDialog progressDialog;
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private String loginPassword, loginEmail;
    private FirebaseAuth mAuth;
    private DatabaseReference facultyRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_faculty);

        // Don't check auth state here as this is a login activity
        init();
    }

    private void init() {
        loginButton = findViewById(R.id.btn_login);
        emailInputLayout = findViewById(R.id.edt_loginEmail);
        passwordInputLayout = findViewById(R.id.edt_loginPassword);

        emailInputLayout.getEditText().addTextChangedListener(this);
        passwordInputLayout.getEditText().addTextChangedListener(this);

        mAuth = FirebaseAuth.getInstance();
        facultyRef = FirebaseDatabase.getInstance().getReference("Faculty");

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                login();
            }
        });
    }

    private void login() {
        loginEmail = emailInputLayout.getEditText().getText().toString().trim();
        loginPassword = passwordInputLayout.getEditText().getText().toString().trim();

        // Validate
        if (loginEmail.isEmpty()) {
            emailInputLayout.setError("Email cannot be empty!");
            return;
        } else if (!isValidEmail(loginEmail)) {
            emailInputLayout.setError("Invalid email or not from 'gmail.com' domain");
            return;
        } else if (loginPassword.isEmpty()) {
            passwordInputLayout.setError("Password cannot be empty!");
            return;
        }

        showProgressDialog("Logging in...");

        // Sign in with email/password
        mAuth.signInWithEmailAndPassword(loginEmail, loginPassword)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d("LoginFaculty", "signInWithEmail:success");
                            // Verify if user is actually a faculty member
                            verifyFacultyStatus();
                        } else {
                            closeProgressDialog();
                            Log.w("LoginFaculty", "signInWithEmail:failure", task.getException());
                            Toast.makeText(getApplicationContext(), "Authentication failed: " +
                                    task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void showProgressDialog(String text) {
        progressDialog = new ProgressDialog(LoginFacultyActivity.this);
        progressDialog.setMessage(text);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void closeProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private boolean isValidEmail(String email) {
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            String domain = email.substring(email.indexOf('@') + 1);
            return domain.equalsIgnoreCase("gmail.com");
        }
        return false;
    }

    // Clear validation errors while typing
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

    private void verifyFacultyStatus() {
        // Check if the authenticated user exists in the Faculty collection
        facultyRef.orderByChild("faculty_email").equalTo(loginEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        closeProgressDialog();
                        if (dataSnapshot.exists()) {
                            // User is a faculty member
                            Log.d("LoginFaculty", "Faculty verification successful");
                            Toast.makeText(getApplicationContext(), "Login successful!", Toast.LENGTH_SHORT).show();
                            
                            // Move to faculty home/attendance page
                            startActivity(new Intent(getApplicationContext(), TakeAttendanceActivity.class));
                            finish(); // prevent going back to login
                        } else {
                            // User is not a faculty member, sign them out
                            Log.w("LoginFaculty", "User is not a faculty member: " + loginEmail);
                            mAuth.signOut(); // Sign out the user
                            Toast.makeText(getApplicationContext(), 
                                    "Access denied. Only faculty members can login here.", 
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        closeProgressDialog();
                        Log.e("LoginFaculty", "Faculty verification failed: " + databaseError.getMessage());
                        // Sign out the user on database error
                        mAuth.signOut();
                        Toast.makeText(getApplicationContext(), 
                                "Verification failed. Please try again.", 
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
