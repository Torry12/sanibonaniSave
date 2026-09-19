package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.FaceMatchResult
import com.sanibonani.save.domain.model.IdDocumentExtraction

/**
 * Repository for AI-powered operations.
 */
interface AiRepository {
    /**
     * Extracts info from ID via Edge Function.
     */
    suspend fun extractIdInfo(idImageBase64: String): Result<IdDocumentExtraction>

    /**
     * Compares faces via Edge Function.
     */
    suspend fun compareFaces(idImageBase64: String, selfieBase64: String): Result<FaceMatchResult>
}
