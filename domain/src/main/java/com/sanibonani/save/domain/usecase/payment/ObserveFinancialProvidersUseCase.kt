package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.model.FinancialProvider
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The providers the app can route a payment through, streamed as they change.
 */
class ObserveFinancialProvidersUseCase @Inject constructor(
    private val financialProviderRepository: FinancialProviderRepository
) {
    operator fun invoke(): Flow<Result<List<FinancialProvider>>> =
        financialProviderRepository.observeProviders()
}
