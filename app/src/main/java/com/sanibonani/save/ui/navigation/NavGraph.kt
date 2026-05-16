package com.sanibonani.save.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
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
import com.sanibonani.save.service.UserProfileCacheService

sealed class Screen(val route: String) {
    data object Landing             : Screen("landing")
    data object Login               : Screen("login")
    data object Register            : Screen("register")
    data object NewUserOnboarding   : Screen("new_user_onboarding")
    data object PasswordRecovery    : Screen("password_recovery")
    data object UpdatePassword  : Screen("update_password")
    data object BrowseGroups    : Screen("browse")
    data object RegisterGroup   : Screen("register_group")
    data object MemberDashboard : Screen("member_dashboard?targetTab={targetTab}&groupId={groupId}&memberId={memberId}") {
        fun withTab(tab: Int, groupId: String? = null, memberId: String? = null) =
            "member_dashboard?targetTab=$tab" +
                (if (groupId != null) "&groupId=$groupId" else "") +
                (if (memberId != null) "&memberId=$memberId" else "")
    }
    data object AdminDashboard  : Screen("admin_dashboard?groupId={groupId}&supportMode={supportMode}") {
        fun withId(groupId: String? = null, supportMode: Boolean = false): String {
            val params = buildList {
                if (groupId != null) add("groupId=$groupId")
                if (supportMode) add("supportMode=true")
            }
            return "admin_dashboard" + if (params.isEmpty()) "" else "?${params.joinToString("&")}" 
        }
    }
    data object PlatformAdmin   : Screen("platform_admin")
    data object CreatePlatformAdmin : Screen("create_platform_admin")

    data object GroupProfile : Screen("group/{groupId}") {
        fun withId(id: String) = "group/$id"
    }
    data object RegisterMember : Screen("join/{groupId}") {
        fun withId(id: String) = "join/$id"
    }
    data object GroupVoting : Screen("group_voting?groupId={groupId}&memberId={memberId}") {
        fun withParams(groupId: String, memberId: String? = null): String {
            return "group_voting?groupId=$groupId" + (if (memberId != null) "&memberId=$memberId" else "")
        }
    }
    data object Payment : Screen("payment/{type}/{amount}/{groupId}") {
        fun build(type: String, amount: String, groupId: String) =
            "payment/$type/$amount/$groupId"
    }
}

private fun String?.isAuthOrPublicRoute(): Boolean {
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

private fun String?.isAuthEntryRoute(): Boolean {
    val route = this ?: return false
    return route.startsWith(Screen.Login.route) ||
        route.startsWith(Screen.Register.route)
}

internal fun destinationForUserRole(role: UserRole, groupId: String? = null): String {
    return when (role) {
        UserRole.PLATFORM_ADMIN -> Screen.PlatformAdmin.route
        UserRole.GROUP_ADMIN -> Screen.AdminDashboard.withId(groupId)
        UserRole.MEMBER -> Screen.MemberDashboard.withTab(0, groupId)
    }
}

internal fun destinationForRoleString(role: String, groupId: String? = null): String {
    val resolvedRole = UserRoleMapper.fromRaw(role) ?: UserRole.MEMBER
    return destinationForUserRole(resolvedRole, groupId)
}

internal fun destinationForPaymentType(type: String, groupId: String): String {
    return when (type) {
        "registration" -> Screen.AdminDashboard.withId(groupId)
        "joining_fee" -> Screen.MemberDashboard.withTab(0, groupId)
        else -> Screen.MemberDashboard.withTab(0, groupId)
    }
}

internal fun shouldForcePlatformAdminRedirect(
    isLoggedIn: Boolean,
    role: UserRole,
    currentRoute: String?,
    isResettingPassword: Boolean
): Boolean {
    // Only force redirect to Platform Admin portal if they are on an auth entry route.
    // This allows them to navigate to public or other authorized pages once logged in.
    return isLoggedIn && role == UserRole.PLATFORM_ADMIN && currentRoute.isAuthEntryRoute() && !isResettingPassword
}

internal fun shouldRedirectAuthenticatedFromEntry(
    currentRoute: String?,
    navigateTo: String?,
    isNewRegistration: Boolean,
    isResettingPassword: Boolean,
    role: UserRole
): Boolean {
    // Redirect all authenticated users from auth entry routes (Login/Register).
    // Additionally, redirect away from Landing page if already logged in.
    val isEntry = currentRoute.isAuthEntryRoute() || currentRoute == Screen.Landing.route
    return isEntry && navigateTo != "login" && !isNewRegistration && !isResettingPassword
}

internal fun shouldRedirectUnauthenticatedToLanding(
    isLoggedIn: Boolean,
    currentRoute: String?
): Boolean {
    return !isLoggedIn && !currentRoute.isAuthOrPublicRoute()
}

/**
 * Helper function to handle navigation to a protected screen.
 * If the user is not logged in, they are redirected to the Login screen with the target route as a callback.
 */
fun NavHostController.navigateProtected(route: String, isLoggedIn: Boolean) {
    if (isLoggedIn) {
        this.navigate(route)
    } else {
        // Encode the route to safely pass it as a parameter
        val encodedRoute = java.net.URLEncoder.encode(route, "UTF-8")
        this.navigate("${Screen.Login.route}?redirect=$encodedRoute")
    }
}

internal fun isRoleAuthorizedForRoute(role: UserRole, currentRoute: String?): Boolean {
    val route = currentRoute ?: return true

    // Public/auth routes remain accessible regardless of role.
    if (route.isAuthOrPublicRoute()) return true

    // Platform admin route: only PLATFORM_ADMIN
    if (route == Screen.PlatformAdmin.route) return role == UserRole.PLATFORM_ADMIN

    // Member dashboard: MEMBER and PLATFORM_ADMIN (if intended)
    if (route.startsWith("member_dashboard")) return role == UserRole.MEMBER || role == UserRole.PLATFORM_ADMIN

    // Admin dashboard: GROUP_ADMIN and PLATFORM_ADMIN
    if (route.startsWith("admin_dashboard")) return role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN

    // Default: only allow if role matches route type
    return false
}

internal fun shouldRedirectForRoleMismatch(
    isLoggedIn: Boolean,
    role: UserRole,
    currentRoute: String?
): Boolean {
    return isLoggedIn && !isRoleAuthorizedForRoute(role, currentRoute)
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

     // Global session observer for forced navigation
     LaunchedEffect(authState.isLoggedIn, authState.userRole, authState.isNewRegistration, authState.navigateTo, currentRoute) {
         if (currentRoute == null) return@LaunchedEffect

         AppLogger.d(
             tag = "NavGraph",
             message = "[Session] User logged in: ${authState.isLoggedIn}, Role: ${authState.userRole}, Current route: $currentRoute"
         )

         if (shouldRedirectForRoleMismatch(authState.isLoggedIn, authState.userRole, currentRoute)) {
             val fallback = destinationForUserRole(authState.userRole)
             AppLogger.d(
                 tag = "NavGraph",
                 message = "[Redirect] Role mismatch: ${authState.userRole}. Navigating from $currentRoute to $fallback."
             )
             navController.navigate(fallback) {
                 popUpTo(0) { inclusive = true }
             }
             return@LaunchedEffect
         }

          if (shouldForcePlatformAdminRedirect(
                 isLoggedIn = authState.isLoggedIn,
                 role = authState.userRole,
                 currentRoute = currentRoute,
                 isResettingPassword = authState.isResettingPassword
             )) {
              AppLogger.d(tag = "NavGraph", message = "[Redirect] Platform admin detected. Navigating to portal from $currentRoute.")
              navController.navigate(Screen.PlatformAdmin.route) {
                  popUpTo(0) { inclusive = true }
              }
              return@LaunchedEffect
          }

          if (authState.isLoggedIn) {
              if (authState.isNewRegistration && currentRoute == Screen.Register.route) {
                  // Platform admins go to their portal; all other new users go to the onboarding chooser
                  val destination = if (authState.userRole == UserRole.PLATFORM_ADMIN) {
                      Screen.PlatformAdmin.route
                  } else {
                      Screen.NewUserOnboarding.route
                  }
                  AppLogger.d(tag = "NavGraph", message = "[Register] New registration detected. Navigating to $destination.")
                  navController.navigate(destination) {
                      popUpTo(0) { inclusive = true }
                  }
                  authViewModel.clearNewRegistrationFlag()
                  return@LaunchedEffect
              }

              if (shouldRedirectAuthenticatedFromEntry(
                      currentRoute = currentRoute,
                      navigateTo = authState.navigateTo,
                      isNewRegistration = authState.isNewRegistration,
                      isResettingPassword = authState.isResettingPassword,
                      role = authState.userRole
                  )) {
                  val dest = destinationForUserRole(authState.userRole)
                  AppLogger.d(tag = "NavGraph", message = "[Redirect] Authenticated user. Navigating to $dest.")
                  navController.navigate(dest) {
                      popUpTo(0) { inclusive = true }
                  }
              }
          } else if (authState.isNewRegistration && currentRoute == Screen.Register.route) {
              AppLogger.d(tag = "NavGraph", message = "[Register] Session not yet authenticated after signup. Redirecting to Login.")
              navController.navigate(Screen.Login.route) {
                  popUpTo(0) { inclusive = true }
              }
              authViewModel.clearNewRegistrationFlag()
          } else if (shouldRedirectUnauthenticatedToLanding(authState.isLoggedIn, currentRoute)) {
              // If not logged in and on a protected screen, go to Landing
              navController.navigate(Screen.Landing.route) {
                  popUpTo(0) { inclusive = true }
              }
          }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Landing.route
    ) {
        composable(Screen.Landing.route) {
            val landingViewModel: com.sanibonani.save.ui.screens.landing.LandingViewModel = hiltViewModel()
            LandingScreen(
                onNavigateLogin = {
                    navController.navigate(Screen.Login.route)
                },
                onNavigateRegisterGroup = {
                    navController.navigateProtected(Screen.RegisterGroup.route, authState.isLoggedIn)
                },
                onNavigateBrowseGroups = {
                    navController.navigate(Screen.BrowseGroups.route)
                },
                onNavigateDashboard = {
                    val dest = destinationForUserRole(authState.userRole)
                    navController.navigate(dest)
                },
                onNavigateMemberPortal = {
                    navController.navigate(Screen.MemberDashboard.withTab(0, null))
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
                         navController.navigate(decodedRedirect) {
                             popUpTo(Screen.Login.route) { inclusive = true }
                         }
                     } else {
                         val destination = destinationForUserRole(role)
                         AppLogger.d(tag = "NavGraph", message = "[Login] Successful login. Routing to $destination for role $role.")
                         navController.navigate(destination) {
                             popUpTo(0) { inclusive = true }
                             launchSingleTop = true
                         }
                     }
                 },
                  onNavigateRegister = { navController.navigate(Screen.Register.route) },
                  onForgotPassword = { navController.navigate(Screen.PasswordRecovery.route) }
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
                 onSuccess = { navController.navigate(Screen.Login.route) { popUpTo(0) } },
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
                    navController.navigate(Screen.RegisterGroup.route) {
                        popUpTo(Screen.NewUserOnboarding.route) { inclusive = false }
                    }
                },
                onBrowseGroups  = {
                    navController.navigate(Screen.BrowseGroups.route) {
                        popUpTo(Screen.NewUserOnboarding.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.BrowseGroups.route) {
            BrowseGroupsScreen(
                onGroupClick = { id -> navController.navigate(Screen.GroupProfile.withId(id)) },
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
            val groupId = back.arguments?.getString("groupId") ?: return@composable
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
                    // After successful group registration and payment, go to Landing page
                    // User can then browse their new group or access admin dashboard from there
                    navController.navigate(Screen.Landing.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RegisterMember.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: return@composable
            RegisterMemberScreen(
                groupId = groupId,
                onMemberRegistered = { amount ->
                    val onboardingType = "joining_fee"
                    val onboardingAmount = amount.toString()
                    navController.navigate(Screen.Payment.build(onboardingType, onboardingAmount, groupId)) {
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
                navArgument("memberId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val targetTab = backStackEntry.arguments?.getInt("targetTab") ?: 0
            val groupId = backStackEntry.arguments?.getString("groupId")
            val memberId = backStackEntry.arguments?.getString("memberId")
            val memberViewModel: MemberViewModel = hiltViewModel()

            // If a specific group/member is passed, switch to that context.
            LaunchedEffect(groupId, memberId) {
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
                    navController.navigate(Screen.Payment.build(type, amount, gid))
                },
                onNavigateAdmin = {
                    val gid = memberViewModel.uiState.value.currentGroupId
                    navController.navigate(Screen.AdminDashboard.withId(gid))
                },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(Screen.Landing.route) { popUpTo(0) }
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

            // If a groupId is explicitly provided, we want to ensure the ViewModel
            // switches to it immediately. This is critical for registration redirect.
            LaunchedEffect(groupId) {
                if (!groupId.isNullOrBlank()) {
                    adminViewModel.selectGroup(groupId)
                }
            }

            AdminDashboardScreen(
                onNavigateToPayment = { type, amount, gid ->
                    navController.navigate(Screen.Payment.build(type, amount, gid))
                },
                onNavigateToMemberPortal = { gid, memberId ->
                    navController.navigate(Screen.MemberDashboard.withTab(0, gid, memberId))
                },
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(Screen.Landing.route) { popUpTo(0) }
                },
                isSupportMode = supportMode,
                vm = adminViewModel
            )
        }

        composable(Screen.PlatformAdmin.route) {
            PlatformAdminScreen(
                onNavigateToCreateAdmin = { navController.navigate(Screen.CreatePlatformAdmin.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(Screen.Landing.route) { popUpTo(0) }
                },
                onImpersonateGroupAdmin = { groupId ->
                    navController.navigate(Screen.AdminDashboard.withId(groupId, supportMode = true))
                },
                onImpersonateMember = { memberId, groupId ->
                    navController.navigate(Screen.MemberDashboard.withTab(0, groupId, memberId))
                },
                onOpenMemberPortalFromDisbursement = { groupId, _ ->
                    navController.navigate(Screen.MemberDashboard.withTab(0, groupId))
                }
            )
        }

        composable(Screen.CreatePlatformAdmin.route) {
            com.sanibonani.save.ui.screens.admin.CreatePlatformAdminScreen(
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
            val groupId = back.arguments?.getString("groupId") ?: return@composable
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
            val type = back.arguments?.getString("type") ?: ""
            val amt = back.arguments?.getString("amount") ?: "0"
            val gid = back.arguments?.getString("groupId") ?: ""
            
                    PaymentScreen(
                        paymentType = type,
                        amount = amt.toDoubleOrNull() ?: 0.0,
                        groupId = gid,
                        onPaymentComplete = {
                            val dest = destinationForPaymentType(type, gid)
                            navController.navigate(dest) {
                                // Clear the stack so back button doesn't go back to payment
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = { 
                            // If they cancel payment, we should probably take them back 
                            // to where they can re-initiate it, or just pop back.
                            navController.popBackStack() 
                        }
                    )
        }
    }
}
