package com.sanibonani.save.di

import com.sanibonani.save.data.remote.SupabaseManager
import com.sanibonani.save.data.repository.*
import com.sanibonani.save.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SupabaseRepoModule {
    @Binds
    @Singleton
    abstract fun bindSupabaseRepository(
        supabaseManager: SupabaseManager
    ): SupabaseRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        groupRepositoryImpl: GroupRepositoryImpl
    ): GroupRepository

    @Binds
    @Singleton
    abstract fun bindMemberRepository(
        memberRepositoryImpl: MemberRepositoryImpl
    ): MemberRepository

    @Binds
    @Singleton
    abstract fun bindBeneficiaryRepository(
        memberRepositoryImpl: MemberRepositoryImpl
    ): BeneficiaryRepository

    @Binds
    @Singleton
    abstract fun bindMemberDocumentRepository(
        memberRepositoryImpl: MemberRepositoryImpl
    ): MemberDocumentRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        notificationRepositoryImpl: NotificationRepositoryImpl
    ): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(
        paymentRepositoryImpl: PaymentRepositoryImpl
    ): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindActuarialRepository(
        actuarialRepositoryImpl: ActuarialRepositoryImpl
    ): ActuarialRepository

    @Binds
    @Singleton
    abstract fun bindPayoutRepository(
        payoutRepositoryImpl: PayoutRepositoryImpl
    ): PayoutRepository

    @Binds
    @Singleton
    abstract fun bindPlatformRepository(
        platformRepositoryImpl: PlatformRepositoryImpl
    ): PlatformRepository

    @Binds
    @Singleton
    abstract fun bindInvestmentRepository(
        investmentRepositoryImpl: InvestmentRepositoryImpl
    ): InvestmentRepository

    @Binds
    @Singleton
    abstract fun bindExportRepository(
        exportRepositoryImpl: ExportRepositoryImpl
    ): ExportRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        storageRepositoryImpl: StorageRepositoryImpl
    ): StorageRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: SyncRepositoryImpl
    ): SyncRepository

    @Binds
    @Singleton
    abstract fun bindCredentialsRepository(
        credentialsRepositoryImpl: CredentialsRepositoryImpl
    ): CredentialsRepository

    @Binds
    @Singleton
    abstract fun bindLoanRepository(
        loanRepositoryImpl: LoanRepositoryImpl
    ): LoanRepository

    @Binds
    @Singleton
    abstract fun bindAgentRepository(
        agentRepositoryImpl: AgentRepositoryImpl
    ): AgentRepository

    @Binds
    @Singleton
    abstract fun bindHealthScoreRepository(
        healthScoreRepositoryImpl: HealthScoreRepositoryImpl
    ): HealthScoreRepository

    @Binds
    @Singleton
    abstract fun bindBeneficiaryClaimRepository(
        beneficiaryClaimRepositoryImpl: BeneficiaryClaimRepositoryImpl
    ): BeneficiaryClaimRepository
}
