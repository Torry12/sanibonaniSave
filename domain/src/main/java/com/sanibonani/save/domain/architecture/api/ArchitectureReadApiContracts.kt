package com.sanibonani.save.domain.architecture.api

import com.sanibonani.save.domain.architecture.AiAgentOpportunity
import com.sanibonani.save.domain.architecture.ApiOperation
import com.sanibonani.save.domain.architecture.FinancialGroupModel
import com.sanibonani.save.domain.architecture.GroupModelBlueprint
import com.sanibonani.save.domain.architecture.PlatformArchitectureBlueprint
import com.sanibonani.save.domain.architecture.PlatformArchitectureBlueprintCatalog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-only API contract layer generated from the architecture blueprint.
 *
 * This provides stable interfaces for route handlers/controllers without changing
 * existing core app logic.
 */
interface ArchitectureReadApi {
    suspend fun getPlatformBlueprint(): Result<PlatformArchitectureBlueprint>
    suspend fun listFinancialModels(): Result<List<FinancialModelSummary>>
    suspend fun getFinancialModel(model: FinancialGroupModel): Result<GroupModelBlueprint>
    suspend fun listApiOperations(model: FinancialGroupModel? = null): Result<List<ApiOperation>>
    suspend fun listAiAgentOpportunities(model: FinancialGroupModel? = null): Result<List<AiAgentOpportunity>>
}

@Serializable
data class FinancialModelSummary(
    @SerialName("model") val model: FinancialGroupModel,
    @SerialName("summary") val summary: String,
    @SerialName("api_count") val apiCount: Int,
    @SerialName("workflow_count") val workflowCount: Int,
    @SerialName("backend_function_count") val backendFunctionCount: Int
)

class BlueprintBackedArchitectureReadApi : ArchitectureReadApi {

    override suspend fun getPlatformBlueprint(): Result<PlatformArchitectureBlueprint> = runCatching {
        PlatformArchitectureBlueprintCatalog.current()
    }

    override suspend fun listFinancialModels(): Result<List<FinancialModelSummary>> = runCatching {
        PlatformArchitectureBlueprintCatalog.current().groupModels.map { model ->
            FinancialModelSummary(
                model = model.model,
                summary = model.summary,
                apiCount = model.apiSurface.size,
                workflowCount = model.workflows.size,
                backendFunctionCount = model.backendFunctions.size
            )
        }
    }

    override suspend fun getFinancialModel(model: FinancialGroupModel): Result<GroupModelBlueprint> = runCatching {
        PlatformArchitectureBlueprintCatalog.forModel(model)
            ?: throw IllegalArgumentException("No blueprint found for model: $model")
    }

    override suspend fun listApiOperations(model: FinancialGroupModel?): Result<List<ApiOperation>> = runCatching {
        val blueprint = PlatformArchitectureBlueprintCatalog.current()
        if (model == null) {
            blueprint.groupModels.flatMap { it.apiSurface }
        } else {
            val found = blueprint.groupModels.firstOrNull { it.model == model }
                ?: throw IllegalArgumentException("No blueprint found for model: $model")
            found.apiSurface
        }
    }

    override suspend fun listAiAgentOpportunities(model: FinancialGroupModel?): Result<List<AiAgentOpportunity>> = runCatching {
        val blueprint = PlatformArchitectureBlueprintCatalog.current()
        if (model == null) {
            blueprint.groupModels.flatMap { it.aiAgentOpportunities }
        } else {
            val found = blueprint.groupModels.firstOrNull { it.model == model }
                ?: throw IllegalArgumentException("No blueprint found for model: $model")
            found.aiAgentOpportunities
        }
    }
}

