package com.sanibonani.save.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.features.admin.ui.AdminDashboardScreen
import com.sanibonani.save.features.admin.ui.BehaviorTrackingDetailScreen
import com.sanibonani.save.features.admin.ui.CreatePlatformAdminScreen
import com.sanibonani.save.features.admin.ui.HealthScoreDetailScreen
import com.sanibonani.save.features.admin.ui.PlatformAdminScreen

/** Group-admin console plus the platform-admin panel and its detail screens. */
internal fun NavGraphBuilder.adminRoutes(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    onNavigatePayment: (type: String, amount: Money, groupId: String) -> Unit,
    onLogout: () -> Unit,
) {
    composable(
        route = Screen.AdminDashboard.route,
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("supportMode") { type = NavType.BoolType; defaultValue = false }
        )
    ) { back ->
        val groupId = back.arguments?.getString("groupId")
        val supportMode = back.arguments?.getBoolean("supportMode") ?: false
        val overviewVm = hiltViewModel<AdminOverviewViewModel>()

        AdminDashboardScreen(
            windowSizeClass = windowSizeClass,
            groupId = groupId,
            onNavigateToPayment = onNavigatePayment,
            onNavigateToMemberPortal = { gid, memberId ->
                navController.navigateSingleTop(
                    Screen.MemberDashboard.withTab(0, gid, memberId, supportMode = supportMode)
                ) {
                    popUpTo(Screen.AdminDashboard.route) { inclusive = true; saveState = true }
                }
            },
            onNavigateToHealthScore = { id ->
                navController.navigateSingleTop(Screen.HealthScoreDetail.withId(id))
            },
            onNavigateBack = { navController.popBackStack() },
            onLogout = onLogout,
            isSupportMode = supportMode,
            overviewVm = overviewVm
        )
    }

    composable(Screen.PlatformAdmin.route) {
        PlatformAdminScreen(
            onNavigateToCreateAdmin = { navController.navigateSingleTop(Screen.CreatePlatformAdmin.route) },
            onNavigateToSandbox = { navController.navigateSingleTop(Screen.PaymentSandbox.route) },
            onLogout = onLogout,
            onImpersonateGroupAdmin = { groupId ->
                navController.navigateSingleTop(Screen.AdminDashboard.withId(groupId, supportMode = true))
            },
            onImpersonateMember = { memberId, groupId ->
                navController.navigateSingleTop(
                    Screen.MemberDashboard.withTab(0, groupId, memberId, supportMode = true)
                )
            },
            onOpenMemberPortalFromDisbursement = { groupId, _ ->
                navController.navigateSingleTop(
                    Screen.MemberDashboard.withTab(0, groupId, supportMode = true)
                )
            },
            onNavigateToHealthScore = { groupId ->
                navController.navigateSingleTop(Screen.HealthScoreDetail.withId(groupId))
            },
            onNavigateToBehaviorTracking = { memberId ->
                navController.navigateSingleTop(Screen.BehaviorTrackingDetail.withId(memberId))
            }
        )
    }

    composable(
        route = Screen.BehaviorTrackingDetail.route,
        arguments = listOf(navArgument("memberId") { type = NavType.StringType })
    ) { back ->
        val memberId = back.requiredStringArg(navController, "memberId", "BehaviorTrackingDetail") ?: return@composable
        BehaviorTrackingDetailScreen(
            memberId = memberId,
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.HealthScoreDetail.route,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
    ) { back ->
        val groupId = back.requiredStringArg(navController, "groupId", "HealthScoreDetail") ?: return@composable
        HealthScoreDetailScreen(
            groupId = groupId,
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.CreatePlatformAdmin.route) {
        CreatePlatformAdminScreen(
            onBack = { navController.popBackStack() }
        )
    }
}
