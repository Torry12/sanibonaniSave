package com.sanibonani.save.data.local

import androidx.room.*

@Database(
    entities = [
        UserProfileEntity::class,
        GroupEntity::class,
        MemberEntity::class,
        BeneficiaryEntity::class,
        ContributionEntity::class,
        PaymentEntity::class,
        NotificationEntity::class,
        MemberDocumentEntity::class,
        PayoutEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class,
        GroupHealthScoreEntity::class,
        BeneficiaryClaimEntity::class,
        LedgerEntryEntity::class,
        SettlementRecordEntity::class,
        ReconciliationRecordEntity::class,
        MemberBehaviorTrackEntity::class,
        FraudDetectionEventEntity::class,
        BehaviorAnalyticsSummaryEntity::class,
        PlatformFeeConfigEntity::class,
        BankTransactionEntity::class,
        BankConnectionEntity::class,
        AuditLogEntity::class,
        AuditReportEntity::class,
        FinancialProviderEntity::class,
        LedgerCheckpointEntity::class,
        GroupPollEntity::class,
        GroupPollOptionEntity::class,
        GroupPollVoteEntity::class,
        PendingOperationEntity::class
    ],
    version = 81,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SanibonaniDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun memberDao(): MemberDao
    abstract fun contributionDao(): ContributionDao
    abstract fun paymentDao(): PaymentDao
    abstract fun beneficiaryDao(): BeneficiaryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun payoutDao(): PayoutDao
    abstract fun memberDocumentDao(): MemberDocumentDao
    abstract fun loanDao(): LoanDao
    abstract fun groupHealthScoreDao(): GroupHealthScoreDao
    abstract fun beneficiaryClaimDao(): BeneficiaryClaimDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun bankTransactionDao(): BankTransactionDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun auditDao(): AuditDao
    abstract fun memberBehaviorTrackDao(): MemberBehaviorTrackDao
    abstract fun fraudDetectionEventDao(): FraudDetectionEventDao
    abstract fun behaviorAnalyticsSummaryDao(): BehaviorAnalyticsSummaryDao
    abstract fun platformFeeConfigDao(): PlatformFeeConfigDao
    abstract fun financialProviderDao(): FinancialProviderDao
    abstract fun ledgerCheckpointDao(): LedgerCheckpointDao
    abstract fun groupVotingDao(): GroupVotingDao
    abstract fun pendingOperationDao(): PendingOperationDao

    suspend fun clearAllData() {
        userProfileDao().deleteProfile("") // Dummy clear
        groupDao().clearAll()
        memberDao().clearAll()
        contributionDao().clearAll()
        paymentDao().clearAll()
        beneficiaryDao().clearAll()
        notificationDao().clearAll()
        payoutDao().clearAll()
        memberDocumentDao().clearAll()
        loanDao().clearAllLoans()
        loanDao().clearAllRepayments()
        groupHealthScoreDao().clearAll()
        beneficiaryClaimDao().clearAll()
        ledgerDao().clearAll()
        ledgerDao().clearAllSettlements()
        bankTransactionDao().clearAll()
        bankTransactionDao().deleteConnectionByGroup("") // Dummy clear
        auditLogDao().clearAll()
        auditDao().clearAll()
        memberBehaviorTrackDao().deleteAll()
        fraudDetectionEventDao().deleteAll()
        behaviorAnalyticsSummaryDao().deleteAll()
        platformFeeConfigDao().clearAll()
        financialProviderDao().clearAll()
        ledgerCheckpointDao().clearAll()
        groupVotingDao().clearAll()
        pendingOperationDao().deleteById("") // Dummy
    }
}
