package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * INVESTMENT REPOSITORY
 */
class InvestmentRepositoryImpl @Inject constructor() : InvestmentRepository {
    override fun getInvestmentOptions(): Flow<Result<List<String>>> = flow {
        emit(Result.success(listOf("Money Market", "Equity Fund", "Government Bonds")))
    }
}
