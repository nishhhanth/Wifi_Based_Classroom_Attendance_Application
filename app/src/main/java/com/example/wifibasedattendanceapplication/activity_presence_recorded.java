package com.example.wifibasedattendanceapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class activity_presence_recorded extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presence_recorded);
        
        // Initialize buttons
        Button showReportBtn = findViewById(R.id.show_report);
        Button exitBtn = findViewById(R.id.exit);
        
        // Set click listeners
        showReportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to attendance report (you may need to pass sessionId if needed)
                Intent intent = new Intent(activity_presence_recorded.this, AttendanceReportActivity.class);
                // If you need to pass sessionId, get it from intent extras
                startActivity(intent);
            }
        });
        
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Logout and clear back stack
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(activity_presence_recorded.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}