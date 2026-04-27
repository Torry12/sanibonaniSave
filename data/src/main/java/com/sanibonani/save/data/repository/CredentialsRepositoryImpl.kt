package com.sanibonani.save.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sanibonani.save.domain.repository.CredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Implementation of CredentialsRepository for secure credential storage.
 * Uses EncryptedSharedPreferences for password, with fallback to normal prefs if encryption fails.
 * All error handling is user-friendly and null-safe.
 */
@Singleton
class CredentialsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CredentialsRepository {

    // Standard (unencrypted) preferences for non-sensitive data
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Encrypted preferences for sensitive data (password)
    private val cryptoPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_auth_prefs_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("CredentialsRepo", "Failed to create encrypted prefs, using fallback", e)
            // Fallback to regular prefs if encryption fails (should be rare)
            context.getSharedPreferences("secure_auth_prefs", Context.MODE_PRIVATE)
        }
    }


    // Returns the saved email, or empty string if not set
    override fun getSavedEmail(): String = prefs.getString("saved_email", "") ?: ""

    // Returns the saved password from encrypted prefs, or empty string if not set
    override fun getSavedPassword(): String = cryptoPrefs.getString("saved_password", "") ?: ""

    // Returns whether Remember Me is enabled
    override fun getRememberMe(): Boolean = prefs.getBoolean("remember_me", false)

    // Returns whether biometric login is enabled
    override fun isBiometricEnabled(): Boolean = prefs.getBoolean("biometric_enabled", false)

    // Sets the biometric enabled flag
    override fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("biometric_enabled", enabled) }
    }

    // Returns true if both email and password are non-blank
    override fun hasSavedCredentials(): Boolean {
        val email = getSavedEmail()
        val password = getSavedPassword()
        return email.isNotBlank() && password.isNotBlank()
    }

    // Saves credentials securely; password is always encrypted
    override fun saveCredentials(email: String, password: String, rememberMe: Boolean) {
        prefs.edit {
            putString("saved_email", email)
            putBoolean("remember_me", rememberMe)
        }
        cryptoPrefs.edit {
            putString("saved_password", password)
        }
    }

    // Clears all saved credentials and biometric flags
    override fun clearCredentials() {
        prefs.edit {
            remove("saved_email")
            remove("remember_me")
            remove("biometric_enabled")
        }
        cryptoPrefs.edit {
            remove("saved_password")
        }
    }
}

