package com.sanibonani.save.di

import com.sanibonani.save.data.remote.SupabaseManager
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
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
