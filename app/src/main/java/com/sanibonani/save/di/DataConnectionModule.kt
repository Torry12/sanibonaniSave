package com.sanibonani.save.di

import com.sanibonani.save.data.remote.SupabaseConnectionManager
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataConnectionModule {

    @Binds
    @Singleton
    abstract fun bindDataConnectionProtocol(impl: SupabaseConnectionManager): DataConnectionProtocol
}
