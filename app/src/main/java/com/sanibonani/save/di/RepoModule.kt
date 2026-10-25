package com.sanibonani.save.di

import com.sanibonani.save.data.repository.admin.*
import com.sanibonani.save.data.repository.auth.*
import com.sanibonani.save.data.repository.credit.LoanRepositoryImpl
import com.sanibonani.save.data.repository.payment.BankRepositoryImpl
import com.sanibonani.save.data.repository.equity.ContributionRepositoryImpl
import com.sanibonani.save.data.repository.payment.FinancialProviderRepositoryImpl
import com.sanibonani.save.data.repository.equity.GroupFinancialRepositoryImpl
import com.sanibonani.save.data.repository.treasury.LedgerRepositoryImpl
import com.sanibonani.save.data.repository.equity.MemberContributionRepositoryImpl
import com.sanibonani.save.data.repository.payment.PaymentGatewayRepositoryImpl
import com.sanibonani.save.data.repository.payment.PaymentRepositoryImpl
import com.sanibonani.save.data.repository.disbursement.PayoutRepositoryImpl
import com.sanibonani.save.data.repository.treasury.ReconciliationRepositoryImpl
import com.sanibonani.save.data.repository.group.*
import com.sanibonani.save.data.repository.member.*
import com.sanibonani.save.data.repository.system.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.payment.PaymentGatewayRepository
import com.sanibonani.save.domain.repository.payment.PaymentRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {

    @Binds
    @Singleton
    abstract fun bindContributionRepository(impl: ContributionRepositoryImpl): ContributionRepository

    @Binds
    @Singleton
    abstract fun bindGroupDocumentRepository(impl: GroupDocumentRepositoryImpl): GroupDocumentRepository

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    @Singleton
    abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository

    @Binds
    @Singleton
    abstract fun bindBeneficiaryRepository(impl: BeneficiaryRepositoryImpl): BeneficiaryRepository

    @Binds
    @Singleton
    abstract fun bindMemberDocumentRepository(impl: MemberDocumentRepositoryImpl): MemberDocumentRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindActuarialRepository(impl: ActuarialRepositoryImpl): ActuarialRepository

    @Binds
    @Singleton
    abstract fun bindPayoutRepository(impl: PayoutRepositoryImpl): PayoutRepository

    @Binds
    @Singleton
    abstract fun bindPlatformRepository(impl: PlatformRepositoryImpl): PlatformRepository

    @Binds
    @Singleton
    abstract fun bindPlatformConfigRepository(impl: PlatformConfigRepositoryImpl): PlatformConfigRepository

    @Binds
    @Singleton
    abstract fun bindInvestmentRepository(impl: InvestmentRepositoryImpl): InvestmentRepository

    @Binds
    @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindStorageNamingStrategy(impl: DefaultStorageNamingStrategy): StorageNamingStrategy

    @Binds
    @Singleton
    abstract fun bindCredentialsRepository(impl: CredentialsRepositoryImpl): CredentialsRepository

    @Binds
    @Singleton
    abstract fun bindLoanRepository(impl: LoanRepositoryImpl): LoanRepository

    @Binds
    @Singleton
    abstract fun bindAgentRepository(impl: AgentRepositoryImpl): AgentRepository

    @Binds
    @Singleton
    abstract fun bindHealthScoreRepository(impl: HealthScoreRepositoryImpl): HealthScoreRepository

    @Binds
    @Singleton
    abstract fun bindBeneficiaryClaimRepository(impl: BeneficiaryClaimRepositoryImpl): BeneficiaryClaimRepository

    @Binds
    @Singleton
    abstract fun bindVotingRepository(impl: VotingRepositoryImpl): VotingRepository

    @Binds
    @Singleton
    abstract fun bindBehaviorTrackingRepository(impl: BehaviorTrackingRepositoryImpl): BehaviorTrackingRepository

    @Binds
    @Singleton
    abstract fun bindPaymentGatewayRepository(impl: PaymentGatewayRepositoryImpl): PaymentGatewayRepository

    @Binds
    @Singleton
    abstract fun bindAuditLogRepository(impl: AuditLogRepositoryImpl): AuditLogRepository

    @Binds
    @Singleton
    abstract fun bindFinancialProviderRepository(impl: FinancialProviderRepositoryImpl): FinancialProviderRepository

    @Binds
    @Singleton
    abstract fun bindBankRepository(impl: BankRepositoryImpl): BankRepository

    @Binds
    @Singleton
    abstract fun bindGroupFinancialRepository(impl: GroupFinancialRepositoryImpl): GroupFinancialRepository

    @Binds
    @Singleton
    abstract fun bindReconciliationRepository(impl: ReconciliationRepositoryImpl): ReconciliationRepository

    @Binds
    @Singleton
    abstract fun bindMemberContributionRepository(impl: MemberContributionRepositoryImpl): MemberContributionRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository
}
