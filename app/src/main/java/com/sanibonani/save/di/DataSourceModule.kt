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
    abstract fun bindRemoteLoanDataSource(impl: SupabaseLoanDataSourceImpl): RemoteLoanDataSource

    @Binds
    @Singleton
    abstract fun bindLocalLoanDataSource(impl: RoomLoanDataSourceImpl): LocalLoanDataSource

    @Binds
    @Singleton
    abstract fun bindRemotePayoutDataSource(impl: SupabasePayoutDataSourceImpl): RemotePayoutDataSource

    @Binds
    @Singleton
    abstract fun bindLocalPayoutDataSource(impl: RoomPayoutDataSourceImpl): LocalPayoutDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteLedgerDataSource(impl: SupabaseLedgerDataSourceImpl): RemoteLedgerDataSource

    @Binds
    @Singleton
    abstract fun bindLocalLedgerDataSource(impl: RoomLedgerDataSourceImpl): LocalLedgerDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteClaimDataSource(impl: SupabaseClaimDataSourceImpl): RemoteClaimDataSource

    @Binds
    @Singleton
    abstract fun bindLocalClaimDataSource(impl: RoomClaimDataSourceImpl): LocalClaimDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteAuditDataSource(impl: SupabaseAuditDataSourceImpl): RemoteAuditDataSource

    @Binds
    @Singleton
    abstract fun bindLocalAuditDataSource(impl: RoomAuditDataSourceImpl): LocalAuditDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteFinancialDataSource(impl: SupabaseFinancialDataSourceImpl): RemoteFinancialDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteReconciliationDataSource(impl: SupabaseReconciliationDataSourceImpl): RemoteReconciliationDataSource

    @Binds
    @Singleton
    abstract fun bindLocalReconciliationDataSource(impl: RoomReconciliationDataSourceImpl): LocalReconciliationDataSource

    @Binds
    @Singleton
    abstract fun bindStorageDataSource(impl: SupabaseStorageDataSourceImpl): StorageDataSource

    @Binds
    @Singleton
    abstract fun bindRealtimeDataSource(impl: SupabaseRealtimeDataSourceImpl): RealtimeDataSource
}
