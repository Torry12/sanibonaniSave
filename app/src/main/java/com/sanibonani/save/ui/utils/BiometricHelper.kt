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
     * Shows the biometric prompt with robust error handling.
     * If activity context is not available, fails gracefully with a user-friendly message.
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
				"Biometric authentication is not available. Please log in with your password."
			)
			return
		}

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Map system error codes to user-friendly messages
                    val userMessage = when (errorCode) {
                        BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                            "Biometric hardware not available on this device."
                        BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                            "Biometric hardware temporarily unavailable."
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            "No biometric data enrolled. Please set up biometrics in device settings."
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                            "Device credentials not set. Please set a PIN or pattern."
                        BiometricPrompt.ERROR_TIMEOUT ->
                            "Authentication timeout. Please try again."
                        else -> errString
                    }
                    onError(errorCode, userMessage)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Handled by system UI
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
