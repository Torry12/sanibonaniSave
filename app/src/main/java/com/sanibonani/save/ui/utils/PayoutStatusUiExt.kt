package com.sanibonani.save.ui.utils

import com.sanibonani.save.domain.model.PayoutStatus

/** User-facing payout status labels shared by group-admin and platform-admin screens. */
val PayoutStatus.uiLabel: String
    get() = when (this) {
        PayoutStatus.PENDING -> "Pending Group Validation"
        PayoutStatus.GROUP_APPROVED -> "Escalated to Platform"
        PayoutStatus.PROCESSING -> "Platform Processing"
        PayoutStatus.COMPLETED -> "Completed"
        PayoutStatus.FAILED -> "Failed"
        PayoutStatus.CANCELLED -> "Cancelled"
    }

