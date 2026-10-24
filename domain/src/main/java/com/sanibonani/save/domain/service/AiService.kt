package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.FaceMatchResult
import com.sanibonani.save.domain.model.IdDocumentExtraction
import java.io.File

/**
 * Service for AI operations including OCR and Facial Recognition.
 */
interface AiService {
    /**
     * Extracts information from an ID document image.
     */
    suspend fun extractIdInfo(documentImage: File): Result<IdDocumentExtraction>

    /**
     * Compares a face in an ID document with a selfie.
     */
    suspend fun compareFaces(documentImage: File, selfieImage: File): Result<FaceMatchResult>
}
