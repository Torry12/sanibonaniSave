package com.sanibonani.save.domain.repository

interface CredentialsRepository {
    fun getSavedEmail(): String
    fun getSavedPassword(): String
    fun getRememberMe(): Boolean
    fun isBiometricEnabled(): Boolean
    fun setBiometricEnabled(enabled: Boolean)
    fun hasSavedCredentials(): Boolean
    fun saveCredentials(email: String, password: String, rememberMe: Boolean)
    fun clearCredentials()
}
