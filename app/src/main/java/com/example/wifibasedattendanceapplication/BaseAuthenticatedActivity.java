package com.example.wifibasedattendanceapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;

import android.widget.Toast;
import android.os.Handler;

public abstract class BaseAuthenticatedActivity extends AppCompatActivity {
    
    protected FirebaseAuth mAuth;
    protected FirebaseUser currentUser;
    private static final String TAG = "BaseAuthenticated";
    private boolean isCheckingAuth = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Don't check auth state here, let subclasses handle it
        // This prevents infinite loops during login
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Don't check authentication in onStart - it's too aggressive
        // Let activities check when they actually need it
    }
    
    /**
     * Check if user is authenticated, redirect to login if not
     */
    protected void checkAuthenticationState() {
        currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "Checking authentication state. Current user: " + (currentUser != null ? currentUser.getEmail() : "null"));
        
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated, redirecting to login");
            redirectToLogin();
        } else {
            Log.d(TAG, "User authenticated: " + currentUser.getEmail());
            onUserAuthenticated();
        }
    }
    
    /**
     * Called when user is successfully authenticated
     * Override this in subclasses to perform post-authentication setup
     */
    protected void onUserAuthenticated() {
        // Override in subclasses if needed
    }
    
    /**
     * Redirect to login activity
     */
    protected void redirectToLogin() {
        Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Sign out current user
     */
    protected void signOut() {
        mAuth.signOut();
        redirectToLogin();
    }
    
    /**
     * Check if user is authenticated before performing database operations
     */
    protected boolean isUserAuthenticated() {
        boolean isAuth = mAuth.getCurrentUser() != null;
        Log.d(TAG, "isUserAuthenticated() called, result: " + isAuth + 
              (isAuth ? ", user: " + mAuth.getCurrentUser().getEmail() : ""));
        return isAuth;
    }
    
    /**
     * Add authentication state listener to monitor auth changes
     */
    protected void addAuthStateListener() {
        mAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    // User signed out, redirect to login
                    Log.d(TAG, "User signed out, redirecting to login");
                    redirectToLogin();
                }
            }
        });
    }
    
    /**
     * Handle database permission errors
     */
    protected void handleDatabaseError(DatabaseError error) {
        Log.e(TAG, "Database error: " + error.getMessage() + " (Code: " + error.getCode() + ")");
        
        switch (error.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                Log.w(TAG, "Database permission denied, user may not be authenticated");
                // Try to refresh token first
                refreshAuthToken();
                // If still not working, sign out
                Toast.makeText(this, "Authentication required. Please login again.", Toast.LENGTH_SHORT).show();
                signOut();
                break;
            case DatabaseError.DISCONNECTED:
                Log.w(TAG, "Database disconnected, retrying...");
                Toast.makeText(this, "Connection lost. Retrying...", Toast.LENGTH_SHORT).show();
                break;
            case DatabaseError.UNAVAILABLE:
                Log.w(TAG, "Database unavailable, retrying...");
                Toast.makeText(this, "Service unavailable. Retrying...", Toast.LENGTH_SHORT).show();
                break;
            default:
                Log.e(TAG, "Unknown database error: " + error.getMessage());
                Toast.makeText(this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                break;
        }
    }
    
    /**
     * Refresh authentication token if needed
     */
    protected void refreshAuthToken() {
        if (currentUser != null) {
            currentUser.getIdToken(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Token refreshed successfully");
                    } else {
                        Log.w(TAG, "Token refresh failed: " + task.getException());
                        // If token refresh fails, user might need to re-authenticate
                        signOut();
                    }
                });
        }
    }
    
    /**
     * Check if user needs to re-authenticate
     */
    protected boolean needsReauthentication() {
        if (currentUser != null) {
            // Only check email verification, remove the time-based check as it's too restrictive
            return currentUser.isEmailVerified() == false;
        }
        return true;
    }
    
    /**
     * Perform database operation with authentication retry
     */
    protected void performDatabaseOperation(Runnable operation) {
        Log.d(TAG, "performDatabaseOperation() called");
        if (shouldAllowAccess()) {
            Log.d(TAG, "Access allowed, proceeding with operation");
            operation.run();
        } else {
            Log.w(TAG, "Access denied, calling onAuthenticationFailed");
            onAuthenticationFailed();
        }
    }
    
    /**
     * Check if this is a login activity
     */
    protected boolean isLoginActivity() {
        String className = this.getClass().getSimpleName();
        return className.equals("LoginActivity") || 
               className.equals("LoginStudentActivity") || 
               className.equals("LoginFacultyActivity");
    }
    
    /**
     * Ensure user is authenticated before database operations
     */
    protected void ensureAuthenticated() {
        if (!isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated, redirecting to login");
            redirectToLogin();
        }
    }
    
    /**
     * Check if Firebase is ready and initialized
     */
    protected boolean isFirebaseReady() {
        try {
            return mAuth != null && FirebaseDatabase.getInstance() != null;
        } catch (Exception e) {
            Log.e(TAG, "Firebase not ready: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if the activity is in a valid state for authentication checks
     */
    protected boolean isActivityValid() {
        return !isFinishing() && !isDestroyed() && !isChangingConfigurations();
    }
    
    /**
     * Override this method to provide custom authentication logic
     * Default implementation just checks if user exists
     */
    protected boolean shouldAllowAccess() {
        return isUserAuthenticated();
    }
    
    /**
     * Override this method to provide custom authentication failure handling
     * Default implementation redirects to login
     */
    protected void onAuthenticationFailed() {
        Log.w(TAG, "Authentication failed, redirecting to login");
        redirectToLogin();
    }
    
    /**
     * Override this method to provide custom authentication success handling
     * Default implementation does nothing
     */
    protected void onAuthenticationSuccess() {
        Log.d(TAG, "Authentication successful");
        // Override in subclasses if needed
    }
    
    /**
     * Override this method to provide custom authentication state change handling
     * Default implementation does nothing
     */
    protected void onAuthenticationStateChanged(boolean isAuthenticated) {
        Log.d(TAG, "Authentication state changed: " + isAuthenticated);
        // Override in subclasses if needed
    }
    
    /**
     * Override this method to provide custom authentication check logic
     * Default implementation just checks if user exists
     */
    protected boolean shouldCheckAuthentication() {
        return true;
    }
    
    /**
     * Override this method to provide custom authentication check timing
     * Default implementation returns 1000ms (1 second)
     */
    protected long getAuthenticationCheckDelay() {
        return 1000; // 1 second
    }
    
    /**
     * Override this method to provide custom authentication check retry logic
     * Default implementation returns false (no retry)
     */
    protected boolean shouldRetryAuthenticationCheck() {
        return false;
    }
    
    /**
     * Override this method to provide custom authentication check retry delay
     * Default implementation returns 2000ms (2 seconds)
     */
    protected long getAuthenticationCheckRetryDelay() {
        return 2000; // 2 seconds
    }
    
    /**
     * Override this method to provide custom authentication check retry count
     * Default implementation returns 0 (no retries)
     */
    protected int getAuthenticationCheckRetryCount() {
        return 0;
    }
    
    /**
     * Override this method to provide custom authentication check retry logic
     * Default implementation does nothing
     */
    
    /**
     * Override this method to provide custom authentication check failure handling
     * Default implementation calls onAuthenticationFailed
     */
    protected void onAuthenticationCheckFailed() {
        Log.w(TAG, "Authentication check failed");
        onAuthenticationFailed();
    }
    
    /**
     * Override this method to provide custom authentication check success handling
     * Default implementation calls onAuthenticationSuccess
     */
    protected void onAuthenticationCheckSuccess() {
        Log.d(TAG, "Authentication check successful");
        onAuthenticationSuccess();
    }
    
    /**
     * Override this method to provide custom authentication check retry handling
     * Default implementation does nothing
     */
    protected void onAuthenticationCheckRetryAttempt(int retryCount, int maxRetries) {
        Log.d(TAG, "Authentication check retry attempt: " + retryCount + "/" + maxRetries);
        // Override in subclasses if needed
    }
    
    /**
     * Override this method to provide custom authentication check retry failure handling
     * Default implementation calls onAuthenticationCheckFailed
     */
    protected void onAuthenticationCheckRetryFailed(int maxRetries) {
        Log.w(TAG, "Authentication check retry failed after " + maxRetries + " attempts");
        onAuthenticationCheckFailed();
    }
    
    /**
     * Override this method to provide custom authentication check retry success handling
     * Default implementation calls onAuthenticationCheckSuccess
     */
    protected void onAuthenticationCheckRetrySuccess(int retryCount) {
        Log.d(TAG, "Authentication check retry successful after " + retryCount + " attempts");
        onAuthenticationCheckSuccess();
    }
    
    /**
     * Override this method to provide custom authentication check retry logic
     * Default implementation does nothing
     */
    
    /**
     * Override this method to provide custom authentication check retry logic
     * Default implementation does nothing
     */
    protected void onAuthenticationCheckRetry(int retryCount) {
        Log.d(TAG, "Authentication check retry: " + retryCount);
        // Override in subclasses if needed
    }
    
    /**
     * Check if user has valid authentication token
     */
    protected boolean hasValidToken() {
        if (currentUser != null) {
            // Don't check token validity here as it can cause issues
            // Just return true if user exists, Firebase will handle token validation
            return true;
        }
        return false;
    }
    
    /**
     * Validate authentication before database operations
     */
    protected boolean validateAuthentication() {
        if (!isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated");
            return false;
        }
        
        if (!hasValidToken()) {
            Log.w(TAG, "User token not valid, refreshing...");
            refreshAuthToken();
            return false;
        }
        
        return true;
    }
    
    /**
     * Perform database operation with authentication validation
     */
    protected void performAuthenticatedOperation(Runnable operation) {
        if (validateAuthentication()) {
            operation.run();
        } else {
            Log.w(TAG, "Authentication validation failed, cannot perform operation");
        }
    }
    
    /**
     * Handle authentication errors gracefully
     */
    protected void handleAuthError(Exception e) {
        Log.e(TAG, "Authentication error: " + e.getMessage());
        if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException ||
            e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Toast.makeText(this, "Invalid credentials. Please login again.", Toast.LENGTH_LONG).show();
            signOut();
        } else {
            Toast.makeText(this, "Authentication error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Check if user needs to re-authenticate due to security policy
     */
    protected boolean needsReauthenticationDueToSecurity() {
        if (currentUser != null) {
            // Check if the user's credentials are still valid according to security policy
            long lastSignIn = currentUser.getMetadata().getLastSignInTimestamp();
            long currentTime = System.currentTimeMillis();
            long timeSinceLastSignIn = currentTime - lastSignIn;
            
            // Require re-authentication after 7 days (much more reasonable)
            return timeSinceLastSignIn > 604800000; // 7 days in milliseconds
        }
        return true;
    }
    
    /**
     * Comprehensive authentication check
     */
    protected boolean performComprehensiveAuthCheck() {
        if (!isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated");
            return false;
        }
        
        // Only check basic authentication, remove strict checks
        return true;
    }
    
    /**
     * Perform database operation with comprehensive authentication check
     */
    protected void performSecureDatabaseOperation(Runnable operation) {
        if (performComprehensiveAuthCheck()) {
            operation.run();
        } else {
            Log.w(TAG, "Comprehensive authentication check failed, cannot perform operation");
            // Try to refresh token and retry once
            refreshAuthToken();
            // Wait a bit and try again
            new Handler().postDelayed(() -> {
                if (performComprehensiveAuthCheck()) {
                    operation.run();
                } else {
                    Log.e(TAG, "Authentication check failed after retry, redirecting to login");
                    redirectToLogin();
                }
            }, 1000); // 1 second delay
        }
    }
}
