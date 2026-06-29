package com.sanibonani.save.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.ui.components.GlobalErrorBanner
import com.sanibonani.save.ui.screens.admin.AdminDashboardScreen
import com.sanibonani.save.ui.screens.admin.PlatformAdminScreen
import com.sanibonani.save.ui.screens.auth.LoginScreen
import com.sanibonani.save.ui.screens.auth.NewUserOnboardingScreen
import com.sanibonani.save.ui.screens.auth.RegisterScreen
import com.sanibonani.save.ui.screens.browse.BrowseGroupsScreen
import com.sanibonani.save.ui.screens.group.GroupProfileScreen
import com.sanibonani.save.ui.screens.group.GroupVotingScreen
import com.sanibonani.save.ui.screens.group.RegisterGroupScreen
import com.sanibonani.save.ui.screens.landing.LandingScreen
import com.sanibonani.save.ui.screens.member.MemberDashboardScreen
import com.sanibonani.save.ui.screens.member.RegisterMemberScreen
import com.sanibonani.save.ui.screens.payment.PaymentScreen
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.viewmodel.AuthViewModel
import com.sanibonani.save.viewmodel.GroupVotingViewModel
import com.sanibonani.save.viewmodel.MemberViewModel
import com.sanibonani.save.viewmodel.GlobalErrorViewModel
import com.sanibonani.save.service.UserProfileCacheService
import java.net.URLEncoder

internal fun encodeRouteValue(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

sealed class Screen(val route: String) {
    data object Landing             : Screen("landing")
    data object Login               : Screen("login")
    data object Register            : Screen("register")
    data object NewUserOnboarding   : Screen("new_user_onboarding")
    data object PasswordRecovery    : Screen("password_recovery")
    data object UpdatePassword  : Screen("update_password")
    data object BrowseGroups    : Screen("browse")
    data object RegisterGroup   : Screen("register_group")
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
    data object PlatformAdmin   : Screen("platform_admin")
    data object CreatePlatformAdmin : Screen("create_platform_admin")
    data object PaymentSandbox : Screen("payment_sandbox")
    data object HealthScoreDetail : Screen("health_score/{groupId}") {
        fun withId(id: String) = "health_score/${encodeRouteValue(id)}"
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

internal object NavigationUtils {
    fun String?.isAuthOrPublicRoute(): Boolean {
        val route = this ?: return false
        return route.startsWith(Screen.Landing.route) ||
                route.startsWith(Screen.Login.route) ||
                route.startsWith(Screen.Register.route) ||
                route.startsWith(Screen.NewUserOnboarding.route) ||
                route.startsWith(Screen.PasswordRecovery.route) ||
                route.startsWith(Screen.UpdatePassword.route) ||
                route.startsWith(Screen.BrowseGroups.route) ||
                route.startsWith("group/")
    }

    fun String?.isAuthEntryRoute(): Boolean {
        val route = this ?: return false
        return route.startsWith(Screen.Login.route) ||
                route.startsWith(Screen.Register.route)
    }

    fun destinationForUserRole(role: UserRole, groupId: String? = null): String {
        return when (role) {
            UserRole.PLATFORM_ADMIN -> Screen.PlatformAdmin.route
            UserRole.GROUP_ADMIN -> Screen.AdminDashboard.withId(groupId)
            UserRole.MEMBER -> Screen.MemberDashboard.withTab(0, groupId)
        }
    }

    fun destinationForRoleString(role: String, groupId: String? = null): String {
        val resolvedRole = UserRoleMapper.fromRaw(role) ?: UserRole.MEMBER
        return destinationForUserRole(resolvedRole, groupId)
    }

    fun destinationForPaymentType(type: String, groupId: String): String {
        return when (type) {
            "registration" -> Screen.AdminDashboard.withId(groupId)
            "joining_fee" -> Screen.MemberDashboard.withTab(0, groupId)
            else -> Screen.MemberDashboard.withTab(0, groupId)
        }
    }

    fun shouldForcePlatformAdminRedirect(
        isLoggedIn: Boolean,
        role: UserRole,
        currentRoute: String?,
        isResettingPassword: Boolean
    ): Boolean {
        return isLoggedIn && role == UserRole.PLATFORM_ADMIN && currentRoute.isAuthEntryRoute() && !isResettingPassword
    }

    fun shouldRedirectAuthenticatedFromEntry(
        currentRoute: String?,
        navigateTo: String?,
        isNewRegistration: Boolean,
        isResettingPassword: Boolean
    ): Boolean {
        val isEntry = currentRoute.isAuthEntryRoute() || currentRoute == Screen.Landing.route
        return isEntry && navigateTo != "login" && !isNewRegistration && !isResettingPassword
    }

    fun shouldRedirectUnauthenticatedToLanding(
        isLoggedIn: Boolean,
        currentRoute: String?
    ): Boolean {
        return !isLoggedIn && !currentRoute.isAuthOrPublicRoute()
    }

    fun isRoleAuthorizedForRoute(
        role: UserRole,
        currentRoute: String?,
        supportMode: Boolean = false
    ): Boolean {
        val route = currentRoute ?: return true
        if (route.isAuthOrPublicRoute()) return true

        if (route == Screen.PlatformAdmin.route ||
            route == Screen.CreatePlatformAdmin.route ||
            route == Screen.PaymentSandbox.route
        ) return role == UserRole.PLATFORM_ADMIN

        if (route == Screen.NewUserOnboarding.route) return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN

        if (route.startsWith("member_dashboard")) {
            return role == UserRole.MEMBER || (role == UserRole.PLATFORM_ADMIN && supportMode)
        }

        if (route.startsWith("admin_dashboard")) {
            return role == UserRole.GROUP_ADMIN || (role == UserRole.PLATFORM_ADMIN && supportMode)
        }

        if (route.startsWith("health_score/")) return role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN

        if (route.startsWith("group_voting")) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        }

        if (route.startsWith("payment/")) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        }

        if (route.startsWith(Screen.RegisterGroup.route) || route.startsWith("join/")) return true

        return false
    }

    fun shouldRedirectForRoleMismatch(
        isLoggedIn: Boolean,
        role: UserRole,
        currentRoute: String?,
        supportMode: Boolean = false
    ): Boolean {
        return isLoggedIn && !isRoleAuthorizedForRoute(role, currentRoute, supportMode)
    }
}

/**
 * Helper function to handle navigation to a protected screen.
 * If the user is not logged in, they are redirected to the Login screen with the target route as a callback.
 */
private fun NavHostController.navigateSingleTop(
    route: String,
    restoreState: Boolean = true,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    // Guard against rapid duplicate taps firing the same navigation repeatedly.
    if (!navigationThrottle.canNavigate(route)) return

    this.navigate(route) {
        launchSingleTop = true
        this.restoreState = restoreState
        builder()
    }
}

/** Used for logout and post-auth flows — does NOT restore stale state. */
private fun NavHostController.navigateAndClearBackStack(route: String) {
    navigateSingleTop(route, restoreState = false) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
    }
}

private val navigationThrottle = object {
    private val lock = Any()
    private var lastRoute: String? = null
    private var lastTime = 0L
    
    fun canNavigate(route: String): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val same = lastRoute == route && (now - lastTime) < 500L
        if (!same) {
            lastRoute = route
            lastTime = now
        }
        !same
    }
}

private val redirectThrottle = object {
    private val lock = Any()
    private var lastRoute: String? = null
    private var lastTime = 0L

    fun canRedirect(route: String): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val same = lastRoute == route && (now - lastTime) < 1000L
        if (!same) {
            lastRoute = route
            lastTime = now
        }
        !same
    }
}

fun NavHostController.navigateProtected(route: String, isLoggedIn: Boolean) {
    if (isLoggedIn) {
        this.navigateSingleTop(route)
    } else {
        // Encode the route to safely pass it as a parameter
        val encodedRoute = java.net.URLEncoder.encode(route, "UTF-8")
        this.navigateSingleTop("${Screen.Login.route}?redirect=$encodedRoute")
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NavGraphEntryPoint {
    fun userProfileCacheService(): UserProfileCacheService
}

@Composable
fun SanibonaniNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userProfileCacheService = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, NavGraphEntryPoint::class.java)
            .userProfileCacheService()
    }
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentSupportMode = navBackStackEntry?.arguments?.getBoolean("supportMode") ?: false

    val errorViewModel: GlobalErrorViewModel = hiltViewModel()
    val errorState by errorViewModel.errorState.collectAsState()

     // Global session observer for forced navigation
     LaunchedEffect(authState.isLoggedIn, authState.userRole, authState.isNewRegistration, authState.navigateTo, currentRoute) {
         if (currentRoute == null) return@LaunchedEffect

         AppLogger.d(
             tag = "NavGraph",
             message = "[Session] User logged in: ${authState.isLoggedIn}, Role: ${authState.userRole}, Current route: $currentRoute"
         )

         // Prevent redirect loops: don't redirect if already navigating
         if (!redirectThrottle.canRedirect(currentRoute)) {
             return@LaunchedEffect
         }

         if (NavigationUtils.shouldRedirectForRoleMismatch(
                 authState.isLoggedIn,
                 authState.userRole,
                 currentRoute,
                 currentSupportMode
             )) {
            val fallback = NavigationUtils.destinationForUserRole(authState.userRole)
            AppLogger.d(
                tag = "NavGraph",
                message = "[Redirect] Role mismatch: ${authState.userRole}. Navigating from $currentRoute to $fallback."
            )
            navController.navigateSingleTop(fallback) {
                // If we are at the destination already (due to tab switching or similar), don't pop anything.
                // Otherwise, pop the current route so it's replaced by the correct one.
                if (currentRoute != fallback) {
                    popUpTo(currentRoute) { inclusive = true }
                }
            }
            return@LaunchedEffect
        }

         if (NavigationUtils.shouldForcePlatformAdminRedirect(
                isLoggedIn = authState.isLoggedIn,
                role = authState.userRole,
                currentRoute = currentRoute,
                isResettingPassword = authState.isResettingPassword
            )) {
             AppLogger.d(tag = "NavGraph", message = "[Redirect] Platform admin detected. Navigating to portal from $currentRoute.")
             navController.navigateSingleTop(Screen.PlatformAdmin.route) {
                 popUpTo(Screen.Landing.route) { inclusive = true }
             }
             return@LaunchedEffect
         }

         if (authState.isLoggedIn) {
             if (authState.isNewRegistration) {
                 // New registration: route to onboarding (or platform admin portal).
                 // Handles two scenarios:
                 //   (a) Right after signup while still on Register screen (session confirmed quickly)
                 //   (b) After email verification → Login → session confirmed (isNewRegistration preserved)
                 val destination = if (authState.userRole == UserRole.PLATFORM_ADMIN) {
                     Screen.PlatformAdmin.route
                 } else {
                     Screen.NewUserOnboarding.route
                 }
                 AppLogger.d(tag = "NavGraph", message = "[Register] New registration detected. Navigating to $destination.")
                 navController.navigateSingleTop(destination) {
                     popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                 }
                 authViewModel.clearNewRegistrationFlag()
                 return@LaunchedEffect
             }

             // Skip redirect when on Login route — LoginScreen handles post-auth navigation
             // via onLoginSuccess, including the redirect parameter from navigateProtected().
             // Otherwise the NavGraph LaunchedEffect and LoginScreen both navigate, causing a
             // double-navigation flicker and potential back-stack corruption.
             if (!currentRoute.startsWith(Screen.Login.route) &&
                 NavigationUtils.shouldRedirectAuthenticatedFromEntry(
                     currentRoute = currentRoute,
                     navigateTo = authState.navigateTo,
                     isNewRegistration = authState.isNewRegistration,
                      isResettingPassword = authState.isResettingPassword
                 )) {
                 val dest = NavigationUtils.destinationForUserRole(authState.userRole)
                 AppLogger.d(tag = "NavGraph", message = "[Redirect] Authenticated user. Navigating to $dest.")
                 navController.navigateSingleTop(dest) {
                     popUpTo(Screen.Landing.route) { inclusive = true }
                 }
             }

         } else if (authState.isNewRegistration && currentRoute == Screen.Register.route) {
             AppLogger.d(tag = "NavGraph", message = "[Register] Session not yet authenticated after signup. Redirecting to Login.")
             navController.navigateSingleTop(Screen.Login.route) {
                 popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
             }
             // Keep isNewRegistration flag — session may confirm later, at which point
             // the LaunchedEffect will route to onboarding when isLoggedIn becomes true.
         } else if (NavigationUtils.shouldRedirectUnauthenticatedToLanding(authState.isLoggedIn, currentRoute)) {
             // If not logged in and on a protected screen, go to Landing
             navController.navigateSingleTop(Screen.Landing.route) {
                 popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
             }
         }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Landing.route
        ) {
            composable(Screen.Landing.route) {
                val landingViewModel: com.sanibonani.save.ui.screens.landing.LandingViewModel = hiltViewModel()
                LandingScreen(
                    onNavigateLogin = {
                        navController.navigateSingleTop(Screen.Login.route)
                    },
                    onNavigateRegisterGroup = {
                        navController.navigateProtected(Screen.RegisterGroup.route, authState.isLoggedIn)
                    },
                    onNavigateBrowseGroups = {
                        navController.navigateSingleTop(Screen.BrowseGroups.route)
                    },
                    onNavigateDashboard = {
                        val dest = NavigationUtils.destinationForUserRole(authState.userRole)
                        navController.navigateSingleTop(dest)
                    },
                    onNavigateMemberPortal = {
                        navController.navigateSingleTop(Screen.MemberDashboard.withTab(0, null))
                    },
                    // onNavigatePlatformAdmin removed; platform admin login is handled after credential verification
                    viewModel = landingViewModel
                )
            }

            composable(
                route = Screen.Login.route + "?redirect={redirect}",
                arguments = listOf(navArgument("redirect") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val redirectRoute = backStackEntry.arguments?.getString("redirect")
                val decodedRedirect = remember(redirectRoute) {
                    redirectRoute?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                }
                LoginScreen(
                    onLoginSuccess = { role ->
                        // Route immediately on successful login so role-based destinations are deterministic
                        // in both production and instrumentation flows.
                        if (decodedRedirect != null) {
                            AppLogger.d(tag = "NavGraph", message = "[Login] Applying protected redirect to $decodedRedirect.")
                            navController.navigateSingleTop(decodedRedirect) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        } else {
                            val destination = NavigationUtils.destinationForUserRole(role)
                            AppLogger.d(tag = "NavGraph", message = "[Login] Successful login. Routing to $destination for role $role.")
                            navController.navigateSingleTop(destination) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        }
                    },
                    onNavigateRegister = { navController.navigateSingleTop(Screen.Register.route) },
                    onForgotPassword = { navController.navigateSingleTop(Screen.PasswordRecovery.route) }
                )
            }
            composable(Screen.PasswordRecovery.route) {
                com.sanibonani.save.ui.screens.auth.PasswordRecoveryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.UpdatePassword.route,
                deepLinks = listOf(navDeepLink { uriPattern = "sanibonani://reset-password" })
            ) {
                com.sanibonani.save.ui.screens.auth.UpdatePasswordScreen(
                    onSuccess = { navController.navigateAndClearBackStack(Screen.Login.route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegistered = { /* Handled by LaunchedEffect */ },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.NewUserOnboarding.route) {
                NewUserOnboardingScreen(
                    profileCache    = userProfileCacheService,
                    onRegisterGroup = {
                        navController.navigateSingleTop(Screen.RegisterGroup.route) {
                            popUpTo(Screen.NewUserOnboarding.route) { inclusive = false }
                        }
                    },
                    onBrowseGroups  = {
                        navController.navigateSingleTop(Screen.BrowseGroups.route) {
                            popUpTo(Screen.NewUserOnboarding.route) { inclusive = false }
                        }
                    }
                )
            }

            composable(Screen.BrowseGroups.route) {
                BrowseGroupsScreen(
                    onGroupClick = { id -> navController.navigateSingleTop(Screen.GroupProfile.withId(id)) },
                    onRegisterGroup = {
                        navController.navigateProtected(Screen.RegisterGroup.route, authState.isLoggedIn)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

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

            composable(Screen.RegisterGroup.route) {
                RegisterGroupScreen(
                    onGroupCreated = {
                        // Navigate directly to the user's role-based dashboard to avoid a
                        // Landing → LaunchedEffect double-hop for logged-in users.
                        val dest = NavigationUtils.destinationForUserRole(authState.userRole)
                        navController.navigateSingleTop(dest, restoreState = false) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
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
                    onMemberRegistered = { amount ->
                        val onboardingType = "joining_fee"
                        val onboardingAmount = amount.toString()
                        navController.navigateSingleTop(Screen.Payment.build(onboardingType, onboardingAmount, groupId)) {
                            popUpTo(Screen.RegisterMember.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
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
                val memberViewModel: MemberViewModel = hiltViewModel()

                // Use the full back-stack entry ID as an additional key so that when the same
                // MemberDashboard route is re-navigated via launchSingleTop with different groupId/memberId
                // params, this effect still re-fires (the back-stack entry ID changes on each navigate()).
                val entryId = backStackEntry.id
                LaunchedEffect(entryId, groupId, memberId) {
                    if (!groupId.isNullOrBlank() && !memberId.isNullOrBlank()) {
                        memberViewModel.beginImpersonation(memberId, groupId)
                    } else if (!groupId.isNullOrBlank()) {
                        memberViewModel.clearImpersonation()
                        memberViewModel.switchGroup(groupId)
                    } else {
                        memberViewModel.clearImpersonation()
                    }
                }

                MemberDashboardScreen(
                    targetTab = targetTab,
                    onNavigatePayment = { type, amount, gid ->
                        navController.navigateSingleTop(Screen.Payment.build(type, amount, gid))
                    },
                    onNavigateAdmin = {
                        val gid = memberViewModel.uiState.value.currentGroupId
                        if (gid.isNullOrBlank()) {
                            AppLogger.e(tag = "NavGraph", message = "[MemberDashboard] No active group to navigate to admin dashboard.")
                            return@MemberDashboardScreen
                        }
                        navController.navigateSingleTop(
                            Screen.AdminDashboard.withId(
                                gid,
                                supportMode = supportMode || authState.userRole == UserRole.PLATFORM_ADMIN
                            )
                        ) {
                            // Pop up to the dashboard base route to avoid stacking portals
                            popUpTo(Screen.MemberDashboard.route) { inclusive = true; saveState = true }
                        }
                    },
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigateAndClearBackStack(Screen.Landing.route)
                    },
                    vm = memberViewModel
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
                val adminViewModel: AdminViewModel = hiltViewModel()

                // Use back-stack entry ID so re-navigation to the same route with a different groupId
                // (e.g. platform admin impersonating different groups) still triggers selectGroup.
                LaunchedEffect(back.id, groupId) {
                    if (!groupId.isNullOrBlank()) {
                        adminViewModel.selectGroup(groupId)
                    }
                }

                AdminDashboardScreen(
                    onNavigateToPayment = { type, amount, gid ->
                        navController.navigateSingleTop(Screen.Payment.build(type, amount, gid))
                    },
                    onNavigateToMemberPortal = { gid, memberId ->
                        navController.navigateSingleTop(
                            Screen.MemberDashboard.withTab(0, gid, memberId, supportMode = supportMode)
                        ) {
                            // Pop up to the admin dashboard base route to avoid stacking portals
                            popUpTo(Screen.AdminDashboard.route) { inclusive = true; saveState = true }
                        }
                    },
                    onNavigateToHealthScore = { groupId ->
                        navController.navigateSingleTop(Screen.HealthScoreDetail.withId(groupId))
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigateAndClearBackStack(Screen.Landing.route)
                    },
                    isSupportMode = supportMode,
                    vm = adminViewModel
                )
            }

            composable(Screen.PlatformAdmin.route) {
                PlatformAdminScreen(
                    onNavigateToCreateAdmin = { navController.navigateSingleTop(Screen.CreatePlatformAdmin.route) },
                    onNavigateToSandbox = { navController.navigateSingleTop(Screen.PaymentSandbox.route) },
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigateAndClearBackStack(Screen.Landing.route)
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
                    }
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
                com.sanibonani.save.ui.screens.admin.HealthScoreDetailScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.CreatePlatformAdmin.route) {
                com.sanibonani.save.ui.screens.admin.CreatePlatformAdminScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PaymentSandbox.route) {
                com.sanibonani.save.ui.screens.admin.PaymentSandboxScreen(
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
                val votingViewModel: GroupVotingViewModel = hiltViewModel()
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

                // Validate all required arguments are present
                if (type.isNullOrBlank() || amt.isNullOrBlank() || gid.isNullOrBlank()) {
                    AppLogger.e(
                        tag = "NavGraph",
                        message = "[Payment] Missing required arguments: type=$type, amount=$amt, groupId=$gid. Popping back."
                    )
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }

                val amount = amt.toDoubleOrNull()
                if (amount == null || amount <= 0) {
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
                            // Clear the stack so back button doesn't go back to payment
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                    onPaymentFailed = { errorViewModel.showError(it.toUserMessage()) },
                    onBack = {
                        // If they cancel payment, take them back to where they can re-initiate
                        navController.popBackStack()
                    }
                )
            }
        }

        errorState.message?.let { msg ->
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GlobalErrorBanner(
                    message = msg,
                    isCritical = errorState.isCritical,
                    actionLabel = errorState.actionLabel,
                    onAction = errorState.onAction,
                    onDismiss = { errorViewModel.dismissError() }
                )
            }
        }
    }
}
