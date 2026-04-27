package com.sanibonani.save.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    // Redundant @Provides methods removed as UseCases now use @Inject constructor with @Singleton scoping where necessary.
}
