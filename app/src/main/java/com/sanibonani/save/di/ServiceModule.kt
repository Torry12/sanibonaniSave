package com.sanibonani.save.di

import com.sanibonani.save.data.service.*
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.service.NetworkConnectivityServiceImpl
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
    abstract fun bindLocationService(impl: LocationServiceImpl): LocationService

    @Binds
    @Singleton
    abstract fun bindAiService(impl: AiServiceImpl): AiService

    @Binds
    @Singleton
    abstract fun bindComplianceService(impl: ComplianceServiceImpl): ComplianceService

    @Binds
    @Singleton
    abstract fun bindDataVerificationService(impl: DataVerificationServiceImpl): DataVerificationService

    @Binds
    @Singleton
    abstract fun bindFraudDetectionService(impl: FraudDetectionServiceImpl): FraudDetectionService

    @Binds
    @Singleton
    abstract fun bindKycService(impl: KycServiceImpl): KycService

    @Binds
    @Singleton
    abstract fun bindMfaService(impl: MfaServiceImpl): MfaService

    @Binds
    @Singleton
    abstract fun bindReconciliationService(impl: ReconciliationServiceImpl): ReconciliationService

    @Binds
    @Singleton
    abstract fun bindSyncTriggerService(impl: SyncTriggerServiceImpl): SyncTriggerService

    @Binds
    @Singleton
    abstract fun bindTreasuryService(impl: TreasuryServiceImpl): TreasuryService

    @Binds
    @Singleton
    abstract fun bindPayoutGuard(impl: PayoutGuardImpl): PayoutGuard

    @Binds
    @Singleton
    abstract fun bindGovernanceService(impl: InMemoryGovernanceService): GovernanceService

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityService(impl: NetworkConnectivityServiceImpl): NetworkConnectivityService

    @Binds
    @Singleton
    abstract fun bindRealtimeSyncService(impl: RealtimeSyncManager): RealtimeSyncService

    @Binds
    @Singleton
    abstract fun bindDevSettingsService(impl: DevSettings): DevSettingsService
}
