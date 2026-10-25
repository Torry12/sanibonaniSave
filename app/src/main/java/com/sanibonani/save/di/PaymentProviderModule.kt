package com.sanibonani.save.di

import com.sanibonani.save.data.remote.gateway.SimulationGateway
import com.sanibonani.save.data.repository.payment.PayFastPaymentProvider
import com.sanibonani.save.data.repository.payment.PaymentGatewayProvider
import com.sanibonani.save.data.repository.payment.SimulatedBankProvider
import com.sanibonani.save.data.repository.payment.StitchPaymentProvider
import com.sanibonani.save.data.repository.payment.SupabaseEdgeBankAdapter
import com.sanibonani.save.domain.model.PaymentMethod
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PaymentProviderModule {

    @Provides
    @Singleton
    fun providePaymentProviders(
        httpClient: HttpClient,
        simulation: SimulationGateway,
        edgeAdapter: SupabaseEdgeBankAdapter
    ): Map<PaymentMethod, PaymentGatewayProvider> = mapOf(
        PaymentMethod.SIMULATED_BANK to SimulatedBankProvider(simulation),
        PaymentMethod.STITCH to StitchPaymentProvider(httpClient),
        PaymentMethod.PAYFAST to PayFastPaymentProvider(httpClient),
        PaymentMethod.BANK to edgeAdapter,
        PaymentMethod.YOCO to edgeAdapter
    )
}
