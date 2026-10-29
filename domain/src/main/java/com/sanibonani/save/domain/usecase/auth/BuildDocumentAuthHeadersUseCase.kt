package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.utils.buildSupabaseAuthHeaders
import javax.inject.Inject

/**
 * The headers a Supabase storage URL needs before the platform will serve a private file.
 */
class BuildDocumentAuthHeadersUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    operator fun invoke(url: String): Map<String, String> =
        buildSupabaseAuthHeaders(url, supabaseRepository.accessToken)
}
