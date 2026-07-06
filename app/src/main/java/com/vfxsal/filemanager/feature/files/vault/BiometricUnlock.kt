package com.vfxsal.filemanager.feature.files.vault

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Fingerprint/face unlock for the vault via the framework BiometricPrompt (API 30+),
 * deliberately avoiding the androidx.biometric artifact - that one requires a
 * FragmentActivity host, and this app's single activity is a plain ComponentActivity.
 * On older devices or hardware without biometrics the PIN path is simply the only option.
 */
object BiometricUnlock {

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /** [context] must be an Activity context so the system sheet has a window to attach to. */
    fun authenticate(context: Context, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt.Builder(context)
            .setTitle("Unlock vault")
            .setNegativeButton("Use PIN", executor) { _, _ -> /* fall back to the PIN field */ }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }
            },
        )
    }
}
