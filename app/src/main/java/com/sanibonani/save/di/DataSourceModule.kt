package com.sanibonani.save.di

import com.sanibonani.save.data.datasource.*
import com.sanibonani.save.data.local.room.*
import com.sanibonani.save.data.remote.supabase.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindAuthDataSource(impl: SupabaseAuthDataSourceImpl): AuthDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteMemberDataSource(impl: SupabaseMemberDataSourceImpl): RemoteMemberDataSource

    @Binds
    @Singleton
    abstract fun bindLocalMemberDataSource(impl: RoomMemberDataSourceImpl): LocalMemberDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteGroupDataSource(impl: SupabaseGroupDataSourceImpl): RemoteGroupDataSource

    @Binds
    @Singleton
    abstract fun bindLocalGroupDataSource(impl: RoomGroupDataSourceImpl): LocalGroupDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteBeneficiaryDataSource(impl: SupabaseBeneficiaryDataSourceImpl): RemoteBeneficiaryDataSource

    @Binds
    @Singleton
    abstract fun bindLocalBeneficiaryDataSource(impl: RoomBeneficiaryDataSourceImpl): LocalBeneficiaryDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteContributionDataSource(impl: SupabaseContributionDataSourceImpl): RemoteContributionDataSource

    @Binds
    @Singleton
    abstract fun bindLocalContributionDataSource(impl: RoomContributionDataSourceImpl): LocalContributionDataSource

    @Binds
    @Singleton
    abstract fun bindStorageDataSource(impl: SupabaseStorageDataSourceImpl): StorageDataSource

    @Binds
    @Singleton
    abstract fun bindRealtimeDataSource(impl: SupabaseRealtimeDataSourceImpl): RealtimeDataSource
}
