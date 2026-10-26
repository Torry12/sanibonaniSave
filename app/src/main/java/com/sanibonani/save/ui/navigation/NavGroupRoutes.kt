package com.sanibonani.save.ui.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.features.auth.presentation.state.AuthState
import com.sanibonani.save.features.group.presentation.GroupVotingViewModel
import com.sanibonani.save.features.group.ui.GroupProfileScreen
import com.sanibonani.save.features.group.ui.GroupRegistrationScreen
import com.sanibonani.save.features.group.ui.GroupVotingScreen
import com.sanibonani.save.features.member.ui.RegisterMemberScreen

/** Group discovery, the creation wizard, joining a group, and group votes. */
internal fun NavGraphBuilder.groupRoutes(
    navController: NavHostController,
    authState: AuthState,
    onGroupCreated: (String) -> Unit,
) {
    composable(
        route = Screen.GroupProfile.route,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        deepLinks = listOf(navDeepLink { uriPattern = "sanibonani://group/{groupId}" })
    ) { back ->
        val groupId = back.requiredStringArg(navController, "groupId", "GroupProfile") ?: return@composable
        GroupProfileScreen(
            groupId = groupId,
            onJoinGroup = {
                navController.navigateProtected(Screen.RegisterMember.withId(groupId), authState.isLoggedIn)
            },
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.RegisterGroup.route,
        arguments = listOf(navArgument("groupType") { type = NavType.StringType; nullable = true })
    ) { back ->
        val groupTypeString = back.arguments?.getString("groupType")
        // An unknown type name from a deep link must not crash the wizard via valueOf().
        val groupType = GroupType.entries.firstOrNull { it.name == groupTypeString }
        GroupRegistrationScreen(
            onSuccess = onGroupCreated,
            onBack = { navController.popBackStack() },
            initialGroupType = groupType
        )
    }

    composable(
        route = Screen.RegisterMember.route,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
    ) { back ->
        val groupId = back.requiredStringArg(navController, "groupId", "RegisterMember") ?: return@composable
        RegisterMemberScreen(
            groupId = groupId,
            onNavigatePayment = { amount ->
                val onboardingType = "joining_fee"
                val onboardingAmount = amount.toPlainString()
                navController.navigateSingleTop(Screen.Payment.build(onboardingType, onboardingAmount, groupId)) {
                    popUpTo(Screen.RegisterMember.route) { inclusive = true }
                }
            },
            onNavigateDashboard = {
                navController.navigateSingleTop(Screen.MemberDashboard.withTab(0, groupId)) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.GroupVoting.route,
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType },
            navArgument("memberId") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { back ->
        val groupId = back.requiredStringArg(navController, "groupId", "GroupVoting") ?: return@composable
        val memberId = back.arguments?.getString("memberId")
        val votingViewModel = hiltViewModel<GroupVotingViewModel>()
        GroupVotingScreen(
            groupId = groupId,
            memberId = memberId,
            vm = votingViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}
