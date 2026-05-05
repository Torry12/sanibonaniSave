package com.sanibonani.save.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the authenticated user's basic profile details (name, email, phone) to
 * SharedPreferences so that every downstream form (RegisterMember, RegisterGroup, etc.)
 * can pre-populate without another network call.
 *
 * Data is written on every successful sign-in / sign-up and cleared on sign-out.
 */
@Singleton
class UserProfileCacheService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_profile_cache", Context.MODE_PRIVATE)

    // ── Write ─────────────────────────────────────────────────────────────────

    fun save(fullName: String, email: String, phone: String = "") {
        prefs.edit {
            putString(KEY_FULL_NAME, fullName.trim())
            putString(KEY_EMAIL, email.trim().lowercase())
            putString(KEY_PHONE, phone.trim())
        }
    }

    fun updatePhone(phone: String) {
        prefs.edit { putString(KEY_PHONE, phone.trim()) }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_FULL_NAME)
            remove(KEY_EMAIL)
            remove(KEY_PHONE)
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getFullName(): String = prefs.getString(KEY_FULL_NAME, "") ?: ""
    fun getEmail(): String    = prefs.getString(KEY_EMAIL, "") ?: ""
    fun getPhone(): String    = prefs.getString(KEY_PHONE, "") ?: ""

    /** Returns true when at least a name and email are available. */
    fun hasProfile(): Boolean = getFullName().isNotBlank() && getEmail().isNotBlank()

    companion object {
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL     = "email"
        private const val KEY_PHONE     = "phone"
    }
}

