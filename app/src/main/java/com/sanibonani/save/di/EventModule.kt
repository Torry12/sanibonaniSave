package com.sanibonani.save.di

import com.sanibonani.save.domain.event.*
import com.sanibonani.save.event.CachesRefreshEventHandler
import com.sanibonani.save.event.PillarRefreshEventHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideAuditLogEventHandler(
        handler: AuditLogEventHandler
    ): EventHandler<DomainEvent> = handler

    @Provides
    @Singleton
    @IntoSet
    fun provideNotificationEventHandler(
        handler: NotificationEventHandler
    ): EventHandler<DomainEvent> = handler

    @Provides
    @Singleton
    @IntoSet
    fun provideBehaviorTrackingEventHandler(
        handler: BehaviorTrackingEventHandler
    ): EventHandler<DomainEvent> = handler

    @Provides
    @Singleton
    @IntoSet
    fun provideCachesRefreshEventHandler(
        handler: CachesRefreshEventHandler
    ): EventHandler<DomainEvent> = handler

    @Provides
    @Singleton
    @IntoSet
    fun provideDisbursementBroadcastHandler(
        handler: DisbursementBroadcastHandler
    ): EventHandler<DomainEvent> = handler

    @Provides
    @Singleton
    @IntoSet
    fun providePillarRefreshEventHandler(
        handler: PillarRefreshEventHandler
    ): EventHandler<DomainEvent> = handler
}
