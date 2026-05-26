package com.sanibonani.save.di

import com.sanibonani.save.domain.event.AuditLogEventHandler
import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.EventHandler
import com.sanibonani.save.domain.event.NotificationEventHandler
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
}
