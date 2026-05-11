package com.sanibonani.save.debug

import android.util.Log
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthDebug {
    private const val TAG = "AuthDebug"

    suspend fun diagnoseLoginFailure(supabaseRepo: SupabaseRepository, email: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "--- Starting Diagnosis for: $email ---")
        
        // 1. Check URL
        Log.d(TAG, "Supabase URL: ${supabaseRepo.supabaseUrl}")
        
        // 2. Check Session
        val session = supabaseRepo.currentSession
        if (session == null) {
            Log.e(TAG, "No active session found.")
        } else {
            Log.d(TAG, "User ID: ${session.user?.id}")
            Log.d(TAG, "User Metadata: ${session.user?.userMetadata}")
            
            // 3. Check Role Logic
            val isCanonical = PlatformAdminAuthPolicy.isPlatformAdminEmail(email)
            Log.d(TAG, "Email matches canonical policy: $isCanonical")
            
            val role = supabaseRepo.getUserRole()
            Log.d(TAG, "Resolved UserRole: $role")
        }
        
        Log.d(TAG, "--- End Diagnosis ---")
    }
}
