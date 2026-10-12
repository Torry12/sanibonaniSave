package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.model.FaceMatchResult
import com.sanibonani.save.domain.model.IdDocumentExtraction
import com.sanibonani.save.domain.repository.system.AiRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val edgeFunctionGateway: EdgeFunctionGateway,
    private val json: Json
) : BaseRepository("AiRepo"), AiRepository {

    override suspend fun extractIdInfo(idImageBase64: String): Result<IdDocumentExtraction> {
        val payload = buildJsonObject {
            put("action", "EXTRACT_ID")
            put("id_image", idImageBase64)
        }

        return edgeFunctionGateway.invoke("process-kyc-ai", payload)
            .map { json.decodeFromJsonElement<IdDocumentExtraction>(it) }
    }

    override suspend fun compareFaces(idImageBase64: String, selfieBase64: String): Result<FaceMatchResult> {
        val payload = buildJsonObject {
            put("action", "COMPARE_FACES")
            put("id_image", idImageBase64)
            put("selfie_image", selfieBase64)
        }

        return edgeFunctionGateway.invoke("process-kyc-ai", payload)
            .map { json.decodeFromJsonElement<FaceMatchResult>(it) }
    }
}

