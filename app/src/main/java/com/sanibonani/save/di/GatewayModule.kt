package com.sanibonani.save.di

import com.sanibonani.save.data.remote.DataBackendEndpointProvider
import com.sanibonani.save.data.remote.gateway.BankSimulationGatewayImpl
import com.sanibonani.save.domain.gateway.BankSimulationGateway
import com.sanibonani.save.domain.service.BackendEndpointProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {

    @Binds
    @Singleton
    abstract fun bindBankSimulationGateway(
        impl: BankSimulationGatewayImpl
    ): BankSimulationGateway

    @Binds
    @Singleton
    abstract fun bindBackendEndpointProvider(
        impl: DataBackendEndpointProvider
    ): BackendEndpointProvider
}
