package com.sanibonani.save.domain.repository.payment
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * The core abstraction for all financial provider interactions.
 * This repository is bank-neutral and routes requests to the
 * appropriate backend orchestrator.
 */
interface FinancialProviderRepository {
    /**
     * Retrieves all available financial providers.
     */
    fun observeProviders(): Flow<Result<List<FinancialProvider>>>

    /**
     * Executes a unified financial operation.
     */
    suspend fun executeOperation(request: FinancialOperationRequest): Result<FinancialOperationResult>

    /**
     * Helper to get a provider by its ID.
     */
    suspend fun getProviderById(id: String): Result<FinancialProvider>
}
