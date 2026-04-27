package com.sanibonani.save.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing investment options and details.
 */
interface InvestmentRepository {
    fun getInvestmentOptions(): Flow<Result<List<String>>>
}
