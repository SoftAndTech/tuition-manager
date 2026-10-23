package com.softandtech.tuitionmanager.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.softandtech.tuitionmanager.R

/** Fingerprint / face unlock as a convenience on top of the PIN (which always remains the fallback). */
object Biometrics {

    /** True if the phone has a fingerprint or face sensor and the user has enrolled one. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                // Errors and cancels simply leave the PIN pad on screen
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.lock_biometric_title))
                .setNegativeButtonText(activity.getString(R.string.lock_use_pin))
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build(),
        )
    }
}
