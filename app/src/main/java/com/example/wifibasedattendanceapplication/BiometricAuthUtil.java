package com.example.wifibasedattendanceapplication;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public final class BiometricAuthUtil {

    public interface Callback {
        void onAuthenticated();
        void onFailed(@NonNull String reason);
        void onError(int code, @NonNull String message);
    }

    private BiometricAuthUtil() { }

    public static boolean isStrongBiometricAvailable(@NonNull Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int res = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return res == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void authenticate(@NonNull androidx.fragment.app.FragmentActivity activity,
                                    @NonNull String title,
                                    @NonNull String subtitle,
                                    @NonNull Callback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt.AuthenticationCallback authCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                callback.onError(errorCode, errString.toString());
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                callback.onAuthenticated();
            }

            @Override
            public void onAuthenticationFailed() {
                callback.onFailed("Authentication failed");
            }
        };

        try {
            // Prefer strong biometrics, but allow device credential fallback to avoid crashes on non-enrolled devices
            int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL;

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .setConfirmationRequired(false)
                    .build();

            BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, authCallback);
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            callback.onError(-1, "Biometric not available: " + e.getMessage());
        }
    }
}


