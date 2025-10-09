package com.example.wifibasedattendanceapplication;

import android.app.Application;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class WifiAttendanceApplication extends Application {
    
    private static final String TAG = "WifiAttendanceApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase Database with offline persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        // Create notification channels
        NotificationHelper.createChannels(this);
        
        // Set up authentication state listener
        FirebaseAuth.getInstance().addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(FirebaseAuth firebaseAuth) {
                if (firebaseAuth.getCurrentUser() != null) {
                    Log.d(TAG, "User signed in: " + firebaseAuth.getCurrentUser().getEmail());
                    // Refresh token periodically
                    firebaseAuth.getCurrentUser().getIdToken(true);
                } else {
                    Log.d(TAG, "User signed out");
                }
            }
        });
        
        Log.d(TAG, "Application initialized");
    }
}
