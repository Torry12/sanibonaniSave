package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class FinancialProviderRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val edgeGateway: EdgeFunctionGateway,
    private val json: Json
) : BaseRepository("FinancialProviderRepository"), FinancialProviderRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()

    override fun observeProviders(): Flow<Result<List<FinancialProvider>>> = observeRemoteOnly(
        networkFetch = {
            supabase.postgrest["financial_providers"].select(columns = Columns.raw("*")) {
                filter { eq("is_active", true) }
            }.decodeList<FinancialProvider>()
        }
    )

    override suspend fun executeOperation(request: FinancialOperationRequest): Result<FinancialOperationResult> = safeRunCatching {
        val payload = json.encodeToJsonElement(FinancialOperationRequest.serializer(), request).jsonObject
        val response = edgeGateway.invoke("financial-orchestrator", payload).getOrThrow()

        json.decodeFromJsonElement(FinancialOperationResult.serializer(), response)
    }

    override suspend fun getProviderById(id: String): Result<FinancialProvider> = safeRunCatching {
        supabase.postgrest["financial_providers"].select(columns = Columns.raw("*")) {
            filter { eq("id", id) }
        }.decodeSingle<FinancialProvider>()
    }
}

