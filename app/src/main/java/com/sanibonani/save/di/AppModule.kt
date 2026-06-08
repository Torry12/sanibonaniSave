package com.sanibonani.save.di

import android.content.Context
import androidx.room.Room
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.local.ALL_MIGRATIONS
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.domain.architecture.policy.InMemoryPolicyRouter
import com.sanibonani.save.domain.architecture.policy.PolicyRouter
import com.sanibonani.save.domain.architecture.policy.LargeAmountDualApprovalRule
import com.sanibonani.save.domain.architecture.policy.SuspendedGroupBlockRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Json Serializer (singleton — used by Supabase and Retrofit) ───────────
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true // Handle uppercase/lowercase mismatches from DB
        encodeDefaults = true
    }

    // ── Supabase Client (singleton — shared across all repositories) ───────────
    @Provides
    @Singleton
    @OptIn(SupabaseInternal::class)
    fun provideSupabaseClient(json: Json, okHttpClient: OkHttpClient): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SUPABASE_URL is empty. Check local.properties.")
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SUPABASE_ANON_KEY is empty. Check local.properties.")

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30.seconds.inWholeMilliseconds
                    connectTimeoutMillis = 30.seconds.inWholeMilliseconds
                    socketTimeoutMillis = 30.seconds.inWholeMilliseconds
                }
                install(Logging) {
                    level = LogLevel.HEADERS
                    logger = object : Logger {
                        override fun log(message: String) {
                            android.util.Log.d("Supabase", message)
                        }
                    }
                }
            }
            
            httpEngine = OkHttp.create {
                preconfigured = okHttpClient
            }
            // Use the shared case-insensitive Json instance
            defaultSerializer = KotlinXSerializer(json)

            install(Auth) {
                autoSaveToStorage = true  // persist JWT in EncryptedSharedPreferences
                alwaysAutoRefresh = true  // silent token refresh
            }
            install(Postgrest)
            install(Storage)              // document & photo uploads
            install(Realtime)             // live group chat & fee status updates
            install(Functions)            // edge function invocations (notifications, enforcement)
        }
    }


    // ── Room Database (offline cache) ──────────────────────────────────────────
    // NOTE: fallbackToDestructiveMigration is intentionally restricted to DEBUG builds.
    // Release builds will throw a MissingMigrationException if a migration path is missing,
    // preventing silent user-data loss. Always add a proper migration when bumping the DB version.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SanibonaniDatabase {
        val builder = Room.databaseBuilder(
            context,
            SanibonaniDatabase::class.java,
            "sanibonani.db"
        ).addMigrations(*ALL_MIGRATIONS)

        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }

        return builder.build()
    }

    // ── Data Repositories ─────────────────────────────────────────────────────
    // Note: Most repositories are now provided via RepoModule using @Binds

    @Provides
    @Singleton
    fun provideGroupDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.GroupDao = db.groupDao()

    @Provides
    @Singleton
    fun provideMemberDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.MemberDao = db.memberDao()

    @Provides
    @Singleton
    fun provideContributionDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.ContributionDao = db.contributionDao()

    @Provides
    @Singleton
    fun providePaymentDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.PaymentDao = db.paymentDao()

    @Provides
    @Singleton
    fun provideBeneficiaryDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.BeneficiaryDao = db.beneficiaryDao()

    @Provides
    @Singleton
    fun provideNotificationDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.NotificationDao = db.notificationDao()

    @Provides
    @Singleton
    fun providePayoutDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.PayoutDao = db.payoutDao()

    @Provides
    @Singleton
    fun provideMemberDocumentDao(db: SanibonaniDatabase): com.sanibonani.save.data.local.MemberDocumentDao = db.memberDocumentDao()

    @Provides
    @Singleton
    fun providePolicyRouter(): PolicyRouter {
        return InMemoryPolicyRouter(
            rules = listOf(
                LargeAmountDualApprovalRule(threshold = 5000.0),
                SuspendedGroupBlockRule()
            )
        )
    }
}
