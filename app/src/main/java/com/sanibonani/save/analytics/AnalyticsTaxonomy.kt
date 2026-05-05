package com.sanibonani.save.analytics

object AnalyticsTaxonomy {
    object Params {
        const val ROLE = "role"
        const val OUTCOME = "outcome"
        const val ERROR_TYPE = "error_type"
        const val GROUP_ID = "group_id"
        const val MEMBER_ID = "member_id"
        const val PAYMENT_TYPE = "payment_type"
        const val PAYOUT_ID = "payout_id"
        const val STATUS = "status"
        const val ENTRY_POINT = "entry_point"
    }

    object Events {
        const val AUTH_SIGN_IN_ATTEMPT = "auth_sign_in_attempt"
        const val AUTH_SIGN_IN_SUCCESS = "auth_sign_in_success"
        const val AUTH_SIGN_IN_FAILURE = "auth_sign_in_failure"
        const val AUTH_MAGIC_LINK_REQUESTED = "auth_magic_link_requested"
        const val AUTH_SIGN_UP_ATTEMPT = "auth_sign_up_attempt"
        const val AUTH_SIGN_UP_SUCCESS = "auth_sign_up_success"
        const val AUTH_SIGN_UP_FAILURE = "auth_sign_up_failure"

        const val LANDING_REFRESH_STARTED = "landing_refresh_started"
        const val LANDING_REFRESH_SUCCESS = "landing_refresh_success"
        const val LANDING_REFRESH_FAILURE = "landing_refresh_failure"

        const val PAYMENT_CONTEXT_LOAD_STARTED = "payment_context_load_started"
        const val PAYMENT_CONTEXT_LOAD_SUCCESS = "payment_context_load_success"
        const val PAYMENT_CONTEXT_LOAD_FAILURE = "payment_context_load_failure"
        const val PAYMENT_PROCESS_STARTED = "payment_process_started"
        const val PAYMENT_PROCESS_SUCCESS = "payment_process_success"
        const val PAYMENT_PROCESS_FAILURE = "payment_process_failure"

        const val PLATFORM_DASHBOARD_LOAD_STARTED = "platform_dashboard_load_started"
        const val PLATFORM_DASHBOARD_LOAD_SUCCESS = "platform_dashboard_load_success"
        const val PLATFORM_DASHBOARD_LOAD_FAILURE = "platform_dashboard_load_failure"
        const val PLATFORM_GROUP_SUSPEND_REQUESTED = "platform_group_suspend_requested"
        const val PLATFORM_GROUP_SUSPEND_SUCCESS = "platform_group_suspend_success"
        const val PLATFORM_GROUP_SUSPEND_FAILURE = "platform_group_suspend_failure"
        const val PLATFORM_GROUP_UNSUSPEND_REQUESTED = "platform_group_unsuspend_requested"
        const val PLATFORM_GROUP_UNSUSPEND_SUCCESS = "platform_group_unsuspend_success"
        const val PLATFORM_GROUP_UNSUSPEND_FAILURE = "platform_group_unsuspend_failure"
        const val PLATFORM_PAYOUT_TRANSITION_REQUESTED = "platform_payout_transition_requested"
        const val PLATFORM_PAYOUT_TRANSITION_SUCCESS = "platform_payout_transition_success"
        const val PLATFORM_PAYOUT_TRANSITION_FAILURE = "platform_payout_transition_failure"
    }
}
