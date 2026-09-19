package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val user: UserProfile? = null,
    val userRole: UserRole = UserRole.MEMBER,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isNewRegistration: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val credentialsRepo: CredentialsRepository
) : BaseViewModel<AuthState>(AuthState()) {

    val state: StateFlow<AuthState> = uiState

    init {
        viewModelScope.launch {
            supabaseRepo.sessionFlow.collect { session ->
                if (session != null) {
                    val role = supabaseRepo.getUserRole()
                    updateState { it.copy(isLoggedIn = true, userRole = role) }
                } else {
                    updateState { it.copy(isLoggedIn = false, userRole = UserRole.MEMBER) }
                }
            }
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            supabaseRepo.signOut()
            onComplete()
        }
    }

    fun handleDeepLink(url: String) { /* logic */ }

    fun clearNewRegistrationFlag() { updateState { it.copy(isNewRegistration = false) } }
}
