package com.sanibonani.save.di

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.repository.system.ConnectionStatus
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataConnectionModule::class]
)
object TestDataConnectionModule {

    @Provides
    @Singleton
    fun provideDataConnectionProtocol(
        supabaseClient: SupabaseClient,
        database: SanibonaniDatabase
    ): DataConnectionProtocol {
        return object : DataConnectionProtocol {
            override val remoteStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connected).asStateFlow()
            override val localStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connected).asStateFlow()
            override suspend fun initialize() = Result.success(Unit)
            override suspend fun shutdown() = Result.success(Unit)
            override suspend fun rebuild() = Result.success(Unit)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getRemoteClient(clazz: Class<T>): T? {
                return if (clazz.isInstance(supabaseClient)) supabaseClient as T else null
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getLocalDatabase(clazz: Class<T>): T? {
                return if (clazz.isInstance(database)) database as T else null
            }
        }
    }
}
