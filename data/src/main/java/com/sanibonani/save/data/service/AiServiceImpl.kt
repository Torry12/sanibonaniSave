package com.sanibonani.save.data.service

import android.util.Base64
import com.sanibonani.save.domain.model.FaceMatchResult
import com.sanibonani.save.domain.model.IdDocumentExtraction
import com.sanibonani.save.domain.repository.system.AiRepository
import com.sanibonani.save.domain.service.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class AiServiceImpl @Inject constructor(
    private val aiRepository: AiRepository
) : AiService {

    override suspend fun extractIdInfo(documentImage: File): Result<IdDocumentExtraction> = withContext(Dispatchers.IO) {
        runCatching {
            val base64 = fileToBase64(documentImage)
            aiRepository.extractIdInfo(base64).getOrThrow()
        }
    }

    override suspend fun compareFaces(documentImage: File, selfieImage: File): Result<FaceMatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val idBase64 = fileToBase64(documentImage)
            val selfieBase64 = fileToBase64(selfieImage)
            aiRepository.compareFaces(idBase64, selfieBase64).getOrThrow()
        }
    }

    private fun fileToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
