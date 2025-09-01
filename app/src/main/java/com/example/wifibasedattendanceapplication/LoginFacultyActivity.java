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

public class LoginFacultyActivity extends BaseAuthenticatedActivity implements TextWatcher {

    private Button loginButton;
    private ProgressDialog progressDialog;
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private String loginPassword, loginEmail;
    private FirebaseAuth mAuth;

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
                        closeProgressDialog();
                        if (task.isSuccessful()) {
                            Log.d("LoginFaculty", "signInWithEmail:success");
                            Toast.makeText(getApplicationContext(), "Login successful!", Toast.LENGTH_SHORT).show();

                            // Move to faculty home/attendance page
                            startActivity(new Intent(getApplicationContext(), TakeAttendanceActivity.class));
                            finish(); // prevent going back to login
                        } else {
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
}
