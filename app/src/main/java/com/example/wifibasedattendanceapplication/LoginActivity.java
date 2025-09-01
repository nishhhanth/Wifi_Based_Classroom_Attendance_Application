package com.example.wifibasedattendanceapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class LoginActivity extends BaseAuthenticatedActivity {

    Button btn_login_to_faculty, btn_login_to_student;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Don't check auth state here as this is a login activity
        btn_login_to_faculty = findViewById(R.id.btn_login_to_faculty);
        btn_login_to_student = findViewById(R.id.btn_login_to_student);

        btn_login_to_student.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext(), LoginStudentActivity.class));
            }
        });

        btn_login_to_faculty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext(), LoginFacultyActivity.class));
            }
        });
    }
}