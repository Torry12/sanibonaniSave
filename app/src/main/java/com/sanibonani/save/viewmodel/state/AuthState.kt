package com.sanibonani.save.viewmodel.state

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.model.UserProfile

data class AuthState(
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirmPw: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val error: String? = null,
    val errorType: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val navigateTo: String? = null,
    val rememberMe: Boolean = false,
    val isNewRegistration: Boolean = false,
    val isResettingPassword: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasSavedCredentials: Boolean = false,
    val activeSupabaseUrl: String = ""
)
