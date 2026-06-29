package com.sanibonani.save.di

import com.sanibonani.save.data.service.LocationServiceImpl
import com.sanibonani.save.domain.service.LocationService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindLocationService(
        impl: LocationServiceImpl
    ): LocationService
}
