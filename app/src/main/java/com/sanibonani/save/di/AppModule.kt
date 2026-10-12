package com.sanibonani.save.di

import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.local.AuditDao
import com.sanibonani.save.data.local.BeneficiaryClaimDao
import com.sanibonani.save.data.local.BeneficiaryDao
import com.sanibonani.save.data.local.ContributionDao
import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.LedgerDao
import com.sanibonani.save.data.local.LoanDao
import com.sanibonani.save.data.local.MemberDao
import com.sanibonani.save.data.local.MemberDocumentDao
import com.sanibonani.save.data.local.NotificationDao
import com.sanibonani.save.data.local.PaymentDao
import com.sanibonani.save.data.local.PayoutDao
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.architecture.policy.InMemoryPolicyRouter
import com.sanibonani.save.domain.architecture.policy.LargeAmountDualApprovalRule
import com.sanibonani.save.domain.architecture.policy.PolicyRouter
import com.sanibonani.save.domain.architecture.policy.SuspendedGroupBlockRule
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Named("geoapify_api_key")
    fun provideGeoapifyApiKey(): String = BuildConfig.GEOAPIFY_API_KEY

    // Json Serializer (singleton - used by Supabase and Retrofit)
    // AI Note: Crucial for deterministic data parsing across different layers.
    // decodeEnumsCaseInsensitive ensures resilience against varying DB string formats.
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true // Handle uppercase/lowercase mismatches from DB
        encodeDefaults = true
    }

    // Supabase Client (delegated to Connection Protocol)
    @Provides
    fun provideSupabaseClient(protocol: DataConnectionProtocol): SupabaseClient {
        return protocol.getRemoteClient(SupabaseClient::class.java)
            ?: throw IllegalStateException("SupabaseClient not initialized in protocol")
    }

    // Room Database (delegated to Connection Protocol)
    @Provides
    fun provideDatabase(protocol: DataConnectionProtocol): SanibonaniDatabase {
        return protocol.getLocalDatabase(SanibonaniDatabase::class.java)
            ?: throw IllegalStateException("SanibonaniDatabase not initialized in protocol")
    }

    // Data Repositories
    // Note: Most repositories are now provided via RepoModule using @Binds

    @Provides
    @Singleton
    fun provideGroupDao(db: SanibonaniDatabase): GroupDao = db.groupDao()

    @Provides
    @Singleton
    fun provideMemberDao(db: SanibonaniDatabase): MemberDao = db.memberDao()

    @Provides
    @Singleton
    fun provideContributionDao(db: SanibonaniDatabase): ContributionDao = db.contributionDao()

    @Provides
    @Singleton
    fun providePaymentDao(db: SanibonaniDatabase): PaymentDao = db.paymentDao()

    @Provides
    @Singleton
    fun provideBeneficiaryDao(db: SanibonaniDatabase): BeneficiaryDao = db.beneficiaryDao()

    @Provides
    @Singleton
    fun provideNotificationDao(db: SanibonaniDatabase): NotificationDao = db.notificationDao()

    @Provides
    @Singleton
    fun providePayoutDao(db: SanibonaniDatabase): PayoutDao = db.payoutDao()

    @Provides
    @Singleton
    fun provideMemberDocumentDao(db: SanibonaniDatabase): MemberDocumentDao = db.memberDocumentDao()

    @Provides
    @Singleton
    fun provideAuditDao(db: SanibonaniDatabase): AuditDao = db.auditDao()

    @Provides
    @Singleton
    fun provideLoanDao(db: SanibonaniDatabase): LoanDao = db.loanDao()

    @Provides
    @Singleton
    fun provideLedgerDao(db: SanibonaniDatabase): LedgerDao = db.ledgerDao()

    @Provides
    @Singleton
    fun provideBeneficiaryClaimDao(db: SanibonaniDatabase): BeneficiaryClaimDao = db.beneficiaryClaimDao()

    @Provides
    @Singleton
    fun providePendingOperationDao(db: SanibonaniDatabase): PendingOperationDao = db.pendingOperationDao()

    @Provides
    @Singleton
    fun providePolicyRouter(): PolicyRouter {
        return InMemoryPolicyRouter(
            rules = listOf(
                LargeAmountDualApprovalRule(threshold = Money(5000.0)),
                SuspendedGroupBlockRule()
            )
        )
    }
}
