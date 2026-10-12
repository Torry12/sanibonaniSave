package com.sanibonani.save.ui.navigation

import java.net.URLEncoder

internal fun encodeRouteValue(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

sealed class Screen(val route: String) {
    /**
     * Route pattern with any query parameters removed.
     *
     * Runtime routes are built by filling the `{placeholders}` in [route], so route
     * comparisons must use [baseRoute] — comparing against [route] itself only ever
     * matches the unfilled pattern.
     */
    val baseRoute: String = route.substringBefore('?')

    data object Landing             : Screen("landing")
    data object Login               : Screen("login")
    data object Register            : Screen("register")
    data object NewUserOnboarding : Screen("new_user_onboarding?groupType={groupType}") {
        fun build(groupType: String? = null): String =
            if (groupType == null) "new_user_onboarding" else "new_user_onboarding?groupType=${encodeRouteValue(groupType)}"
    }
    data object PasswordRecovery    : Screen("password_recovery")
    data object UpdatePassword      : Screen("update_password")
    data object BrowseGroups        : Screen("browse")
    data object RegisterGroup : Screen("register_group?groupType={groupType}") {
        fun build(groupType: String? = null): String =
            if (groupType == null) "register_group" else "register_group?groupType=${encodeRouteValue(groupType)}"
    }

    data object MemberDashboard : Screen("member_dashboard?targetTab={targetTab}&groupId={groupId}&memberId={memberId}&supportMode={supportMode}") {
        fun withTab(
            tab: Int,
            groupId: String? = null,
            memberId: String? = null,
            supportMode: Boolean = false
        ) =
            "member_dashboard?targetTab=$tab" +
                (if (groupId != null) "&groupId=${encodeRouteValue(groupId)}" else "") +
                (if (memberId != null) "&memberId=${encodeRouteValue(memberId)}" else "") +
                (if (supportMode) "&supportMode=true" else "")
    }

    data object AdminDashboard  : Screen("admin_dashboard?groupId={groupId}&supportMode={supportMode}") {
        fun withId(groupId: String? = null, supportMode: Boolean = false): String {
            val params = buildList {
                if (groupId != null) add("groupId=${encodeRouteValue(groupId)}")
                if (supportMode) add("supportMode=true")
            }
            return "admin_dashboard" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        }
    }

    data object PlatformAdmin       : Screen("platform_admin")
    data object CreatePlatformAdmin : Screen("create_platform_admin")
    data object PaymentSandbox      : Screen("payment_sandbox")

    data object HealthScoreDetail : Screen("health_score/{groupId}") {
        fun withId(id: String) = "health_score/${encodeRouteValue(id)}"
    }

    data object BehaviorTrackingDetail : Screen("behavior_tracking/{memberId}") {
        fun withId(id: String) = "behavior_tracking/${encodeRouteValue(id)}"
    }

    data object GroupProfile : Screen("group/{groupId}") {
        fun withId(id: String) = "group/${encodeRouteValue(id)}"
    }

    data object RegisterMember : Screen("join/{groupId}") {
        fun withId(id: String) = "join/${encodeRouteValue(id)}"
    }

    data object GroupVoting : Screen("group_voting?groupId={groupId}&memberId={memberId}") {
        fun withParams(groupId: String, memberId: String? = null): String {
            return "group_voting?groupId=${encodeRouteValue(groupId)}" +
                (if (memberId != null) "&memberId=${encodeRouteValue(memberId)}" else "")
        }
    }

    data object Payment : Screen("payment/{type}/{amount}/{groupId}") {
        fun build(type: String, amount: String, groupId: String) =
            "payment/${encodeRouteValue(type)}/${encodeRouteValue(amount)}/${encodeRouteValue(groupId)}"
    }
}
