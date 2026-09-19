package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.model.NotifEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides the routing logic for notifications based on business rules.
 */
@Singleton
class NotificationRouter @Inject constructor() {

    fun shouldRouteToAdmin(event: NotifEvent): Boolean {
        return event == NotifEvent.MEMBER_MESSAGE ||
            event == NotifEvent.LOAN_REQUESTED ||
            event == NotifEvent.PAYOUT_REQUESTED
    }
}
