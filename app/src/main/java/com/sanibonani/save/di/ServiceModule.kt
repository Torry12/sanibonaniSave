package com.sanibonani.save.di

import com.sanibonani.save.data.repository.*
import com.sanibonani.save.domain.service.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindKycService(impl: KycServiceImpl): KycService

    @Binds
    @Singleton
    abstract fun bindFraudDetectionService(impl: FraudDetectionServiceImpl): FraudDetectionService

    @Binds
    @Singleton
    abstract fun bindComplianceService(impl: ComplianceServiceImpl): ComplianceService

    @Binds
    @Singleton
    abstract fun bindReconciliationService(impl: ReconciliationServiceImpl): ReconciliationService
}
