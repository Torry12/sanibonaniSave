package com.sanibonani.save.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.ui.components.GlobalErrorBanner
import com.sanibonani.save.features.common.presentation.GlobalSyncViewModel
import com.sanibonani.save.ui.components.GlobalSyncBanner
import com.sanibonani.save.ui.components.GlobalLoadingOverlay
import com.sanibonani.save.features.admin.ui.AdminDashboardScreen
import com.sanibonani.save.features.admin.ui.PlatformAdminScreen
import com.sanibonani.save.features.admin.ui.BehaviorTrackingDetailScreen
import com.sanibonani.save.features.admin.ui.HealthScoreDetailScreen
import com.sanibonani.save.features.admin.ui.CreatePlatformAdminScreen
import com.sanibonani.save.features.group.ui.GroupProfileScreen
import com.sanibonani.save.features.group.ui.GroupVotingScreen
import com.sanibonani.save.features.group.ui.GroupRegistrationScreen
import com.sanibonani.save.features.member.ui.MemberDashboardScreen
import com.sanibonani.save.features.member.ui.RegisterMemberScreen
import com.sanibonani.save.features.financial.ui.PaymentScreen
import com.sanibonani.save.features.financial.ui.PaymentSandboxScreen
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.features.group.presentation.GroupVotingViewModel
import com.sanibonani.save.features.common.presentation.GlobalErrorViewModel
import com.sanibonani.save.features.common.presentation.GlobalLoadingViewModel
import com.sanibonani.save.service.UserProfileCacheService

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NavGraphEntryPoint {
    fun userProfileCacheService(): UserProfileCacheService
}

@Composable
fun SanibonaniNavGraph(
    navController: NavHostController = rememberNavController(),
    windowSizeClass: WindowSizeClass
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userProfileCacheService = remember(context) {
        try {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, NavGraphEntryPoint::class.java)
                .userProfileCacheService()
        } catch (e: Exception) {
            AppLogger.e("NavGraph", "Hilt Entry Point not found. This might be a test environment without HiltAndroidRule.")
            null
        }
    }
    val authViewModel = hiltViewModel<AuthViewModel>()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentSupportMode = navBackStackEntry?.arguments?.getBoolean("supportMode") ?: false

val errorViewModel = hiltViewModel<GlobalErrorViewModel>()
val errorState by errorViewModel.errorState.collectAsStateWithLifecycle()
val globalLoadingViewModel = hiltViewModel<GlobalLoadingViewModel>()
val globalSpinnerActive by globalLoadingViewModel.showSpinner.collectAsStateWithLifecycle()

    val syncViewModel = hiltViewModel<GlobalSyncViewModel>()
    val syncStatus by syncViewModel.syncStatus.collectAsStateWithLifecycle()

     // ── Consolidated Navigation Observer ─────────────────────────────────────────────
     LaunchedEffect(authState.isLoggedIn, authState.userRole, authState.isNewRegistration, currentRoute) {
         val route = currentRoute ?: return@LaunchedEffect
         if (!redirectThrottle.canRedirect(route)) return@LaunchedEffect

         val destination = RouteGuard.getRedirect(
             isLoggedIn = authState.isLoggedIn,
             role = authState.userRole,
             currentRoute = route,
             isNewRegistration = authState.isNewRegistration,
             isResettingPassword = authState.isResettingPassword,
             supportMode = currentSupportMode
         )

         if (destination != null) {
             navController.navigateSingleTop(destination) {
                 popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
             }
             if (authState.isNewRegistration) {
                 authViewModel.clearNewRegistrationFlag()
             }
         }
    }

    // Group creation finishes after the role has been elevated server-side, but the auth state
    // still holds the pre-creation role. Waiting for the refreshed role keeps the creator out of
    // a group-less dashboard that the route guard would immediately bounce them from.
    var createdGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(createdGroupId, authState.userRole) {
        val groupId = createdGroupId ?: return@LaunchedEffect
        if (authState.userRole == UserRole.MEMBER) {
            // A failed or slow role lookup must not strand the user on the success screen;
            // fall through to the member dashboard still scoped to the new group.
            delay(3_000)
        }
        createdGroupId = null
        val destination = NavigationUtils.destinationAfterGroupCreation(authState.userRole, groupId)
        navController.navigateSingleTop(destination, restoreState = false) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Landing.route
        ) {
            authRoutes(
                navController = navController,
                authState = authState,
                userProfileCacheService = userProfileCacheService
            )

            composable(
                route = Screen.GroupProfile.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "sanibonani://group/{groupId}" })
            ) { back ->
                val groupId = back.arguments?.getString("groupId")
                if (groupId.isNullOrBlank()) {
                    AppLogger.e(tag = "NavGraph", message = "[GroupProfile] Missing required groupId argument. Popping back.")
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
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
                    onSuccess = { groupId ->
                        createdGroupId = groupId
                        authViewModel.refreshUserRole()
                    },
                    onBack = { navController.popBackStack() },
                    initialGroupType = groupType
                )
            }

            composable(
                route = Screen.RegisterMember.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { back ->
                val groupId = back.arguments?.getString("groupId")
                if (groupId.isNullOrBlank()) {
                    AppLogger.e(tag = "NavGraph", message = "[RegisterMember] Missing required groupId argument. Popping back.")
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
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
                route = Screen.MemberDashboard.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "sanibonani://member_dashboard?targetTab={targetTab}&groupId={groupId}" }
                ),
                arguments = listOf(
                    navArgument("targetTab") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("memberId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("supportMode") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val targetTab = backStackEntry.arguments?.getInt("targetTab") ?: 0
                val groupId = backStackEntry.arguments?.getString("groupId")
                val memberId = backStackEntry.arguments?.getString("memberId")
                val supportMode = backStackEntry.arguments?.getBoolean("supportMode") ?: false
                val overviewVm = hiltViewModel<MemberOverviewViewModel>()

                val entryId = backStackEntry.id
                LaunchedEffect(entryId, groupId, memberId) {
                    overviewVm.setPortalContext(memberId, groupId)
                }

                MemberDashboardScreen(
                    targetTab = targetTab,
                    windowSizeClass = windowSizeClass,
                    onNavigatePayment = { type, amount, gid ->
                        navController.navigateSingleTop(Screen.Payment.build(type, amount.amount.toPlainString(), gid))
                    },
                    onNavigateAdmin = {
                        val gid = overviewVm.state.value.selectedGroupId
                        if (gid.isNullOrBlank()) {
                            AppLogger.e(tag = "NavGraph", message = "[MemberDashboard] No active group to navigate to admin dashboard.")
                            return@MemberDashboardScreen
                        }
                        navController.navigateSingleTop(
                            Screen.AdminDashboard.withId(
                                gid,
                                supportMode = supportMode || (authState.userRole == UserRole.PLATFORM_ADMIN)
                            )
                        ) {
                            popUpTo(Screen.MemberDashboard.route) { inclusive = true; saveState = true }
                        }
                    },
                    onLogout = {
                        authViewModel.signOut {
                            navController.navigateAndClearBackStack(Screen.Landing.route)
                        }
                    },
                    onExitSupport = {
                        navController.popBackStack()
                    },
                    overviewVm = overviewVm
                )
            }

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
                    onNavigateToPayment = { type, amount, gid ->
                        navController.navigateSingleTop(Screen.Payment.build(type, amount.amount.toPlainString(), gid))
                    },
                    onNavigateToMemberPortal = { gid, memberId ->
                        navController.navigateSingleTop(
                            Screen.MemberDashboard.withTab(0, gid, memberId, supportMode = supportMode)
                        ) {
                            popUpTo(Screen.AdminDashboard.route) { inclusive = true; saveState = true }
                        }
                    },
                    onNavigateToHealthScore = { groupId ->
                        navController.navigateSingleTop(Screen.HealthScoreDetail.withId(groupId))
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        authViewModel.signOut {
                            navController.navigateAndClearBackStack(Screen.Landing.route)
                        }
                    },
                    isSupportMode = supportMode,
                    overviewVm = overviewVm
                )
            }

            composable(Screen.PlatformAdmin.route) {
                PlatformAdminScreen(
                    onNavigateToCreateAdmin = { navController.navigateSingleTop(Screen.CreatePlatformAdmin.route) },
                    onNavigateToSandbox = { navController.navigateSingleTop(Screen.PaymentSandbox.route) },
                    onLogout = {
                        authViewModel.signOut {
                            navController.navigateAndClearBackStack(Screen.Landing.route)
                        }
                    },
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
                val memberId = back.arguments?.getString("memberId") ?: return@composable
                BehaviorTrackingDetailScreen(
                    memberId = memberId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.HealthScoreDetail.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { back ->
                val groupId = back.arguments?.getString("groupId")
                if (groupId.isNullOrBlank()) {
                    AppLogger.e(tag = "NavGraph", message = "[HealthScoreDetail] Missing required groupId argument. Popping back.")
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
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

            composable(Screen.PaymentSandbox.route) {
                PaymentSandboxScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.GroupVoting.route,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("memberId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { back ->
                val groupId = back.arguments?.getString("groupId")
                if (groupId.isNullOrBlank()) {
                    AppLogger.e(tag = "NavGraph", message = "[GroupVoting] Missing required groupId argument. Popping back.")
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                val memberId = back.arguments?.getString("memberId")
                val votingViewModel = hiltViewModel<GroupVotingViewModel>()
                GroupVotingScreen(
                    groupId = groupId,
                    memberId = memberId,
                    vm = votingViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Payment.route,
                arguments = listOf(
                    navArgument("type")    { type = NavType.StringType },
                    navArgument("amount")  { type = NavType.StringType },
                    navArgument("groupId") { type = NavType.StringType }
                )
            ) { back ->
                val type = back.arguments?.getString("type")
                val amt = back.arguments?.getString("amount")
                val gid = back.arguments?.getString("groupId")

                if (type.isNullOrBlank() || amt.isNullOrBlank() || gid.isNullOrBlank()) {
                    AppLogger.e(tag = "NavGraph", message = "[Payment] Missing required arguments. Popping back.")
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }

                val amount = amt.let { try { Money(java.math.BigDecimal(it)) } catch(e: Exception) { null } }
                if (amount == null || !amount.isPositive) {
                    AppLogger.e(tag = "NavGraph", message = "[Payment] Invalid amount: $amt. Popping back.")
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }

                PaymentScreen(
                    paymentType = type,
                    amount = amount,
                    groupId = gid,
                    onPaymentComplete = {
                        val dest = NavigationUtils.destinationForPaymentType(type, gid)
                        navController.navigateSingleTop(dest) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                    onPaymentFailed = { errorViewModel.showError(it.toUserMessage()) },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        GlobalLoadingOverlay(globalSpinnerActive)

        GlobalSyncBanner(syncStatus)

        errorState.message?.let { msg ->
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GlobalErrorBanner(
                    message = msg,
                    onRetry = { /* logic */ },
                    onDismiss = { errorViewModel.dismissError() },
                    isCritical = errorState.isCritical,
                    actionLabel = errorState.actionLabel,
                    onAction = errorState.onAction
                )
            }
        }
    }
}
