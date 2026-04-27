package com.sanibonani.save.ui.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper to handle Biometric (Fingerprint/Face/PIN) authentication.
 */
object BiometricHelper {

  private val allowedAuthenticators: Int =
    // BIOMETRIC_WEAK includes common face/fingerprint implementations across devices.
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

  private fun findFragmentActivity(context: Context): FragmentActivity? {
    var ctx = context
    while (ctx is ContextWrapper) {
      if (ctx is FragmentActivity) return ctx
      ctx = ctx.baseContext
    }
    return null
  }

    /**
     * Checks if the device supports and has biometric enrollment.
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
		return biometricManager.canAuthenticate(allowedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the biometric prompt.
     */
    fun showBiometricPrompt(
        context: Context,
        title: String = "Biometric Authentication",
        subtitle: String = "Log in using your device credentials",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (Int, CharSequence) -> Unit
    ) {
		val activity = findFragmentActivity(context)
		if (activity == null) {
			onError(
				BiometricPrompt.ERROR_HW_UNAVAILABLE,
				"Biometric login is not available on this screen. Please try again."
			)
			return
		}

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Usually handled by the system UI, but we could notify the caller
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                allowedAuthenticators
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
