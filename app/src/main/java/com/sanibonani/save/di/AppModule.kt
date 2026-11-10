package com.sanibonani.save.di

import android.content.Context
import androidx.work.WorkManager
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.architecture.policy.InMemoryPolicyRouter
import com.sanibonani.save.domain.architecture.policy.LargeAmountDualApprovalRule
import com.sanibonani.save.domain.architecture.policy.PolicyRouter
import com.sanibonani.save.domain.architecture.policy.SuspendedGroupBlockRule
import com.sanibonani.save.domain.service.FinancialSecurityService
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
    //
    // No DAO is provided here on purpose. A @Singleton DAO is bound to whichever database the graph
    // asked for first, so it keeps reading a cache the manager has since closed; every reader takes
    // Provider<SanibonaniDatabase> and resolves its DAO from the live cache instead. LiveRoomCacheAccessTest
    // fails the build if a singleton DAO provider or a frozen cache reference comes back.

    @Provides
    @Singleton
    fun providePolicyRouter(): PolicyRouter {
        return InMemoryPolicyRouter(
            rules = listOf(
                LargeAmountDualApprovalRule(threshold = FinancialSecurityService.DUAL_CONTROL_THRESHOLD),
                SuspendedGroupBlockRule()
            )
        )
    }

    // WorkManager is initialised on demand by SanibonaniApp's Configuration.Provider, which
    // installs the Hilt worker factory, so getInstance() is safe from any injection point.
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
