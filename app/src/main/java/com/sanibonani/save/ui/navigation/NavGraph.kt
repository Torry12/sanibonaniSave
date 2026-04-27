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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.ui.screens.admin.AdminDashboardScreen
import com.sanibonani.save.ui.screens.admin.PlatformAdminScreen
import com.sanibonani.save.ui.screens.auth.LoginScreen
import com.sanibonani.save.ui.screens.auth.RegisterScreen
import com.sanibonani.save.ui.screens.browse.BrowseGroupsScreen
import com.sanibonani.save.ui.screens.group.GroupProfileScreen
import com.sanibonani.save.ui.screens.group.RegisterGroupScreen
import com.sanibonani.save.ui.screens.landing.LandingScreen
import com.sanibonani.save.ui.screens.member.MemberDashboardScreen
import com.sanibonani.save.ui.screens.member.RegisterMemberScreen
import com.sanibonani.save.ui.screens.payment.PaymentScreen
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.viewmodel.AuthViewModel
import com.sanibonani.save.viewmodel.MemberViewModel

sealed class Screen(val route: String) {
    data object Landing         : Screen("landing")
    data object Login           : Screen("login")
    data object Register        : Screen("register")
    data object PasswordRecovery: Screen("password_recovery")
    data object UpdatePassword  : Screen("update_password")
    data object BrowseGroups    : Screen("browse")
    data object RegisterGroup   : Screen("register_group")
    data object MemberDashboard : Screen("member_dashboard?targetTab={targetTab}&groupId={groupId}") {
        fun withTab(tab: Int, groupId: String? = null) = "member_dashboard?targetTab=$tab${if (groupId != null) "&groupId=$groupId" else ""}"
    }
    data object AdminDashboard  : Screen("admin_dashboard?groupId={groupId}") {
        fun withId(groupId: String? = null) = "admin_dashboard" + (if (groupId != null) "?groupId=$groupId" else "")
    }
    data object PlatformAdmin   : Screen("platform_admin")
    data object CreatePlatformAdmin : Screen("create_platform_admin")

    data object GroupProfile : Screen("group/{groupId}") {
        fun withId(id: String) = "group/$id"
    }
    data object RegisterMember : Screen("join/{groupId}") {
        fun withId(id: String) = "join/$id"
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
        route.startsWith(Screen.PasswordRecovery.route) ||
        route.startsWith(Screen.UpdatePassword.route) ||
        route.startsWith(Screen.BrowseGroups.route) ||
        route.startsWith("group/")
}

private fun String?.isAuthEntryRoute(): Boolean {
    val route = this ?: return false
    return route.startsWith(Screen.Login.route) ||
        route.startsWith(Screen.Register.route) ||
        route.startsWith(Screen.UpdatePassword.route)
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
    currentRoute: String?
): Boolean {
    // Only force redirect to Platform Admin portal if they are on an auth entry route.
    // This allows them to navigate to public or other authorized pages once logged in.
    return isLoggedIn && role == UserRole.PLATFORM_ADMIN && currentRoute.isAuthEntryRoute()
}

internal fun shouldRedirectAuthenticatedFromEntry(
    currentRoute: String?,
    navigateTo: String?,
    isNewRegistration: Boolean,
    role: UserRole
): Boolean {
    // For regular users, we also consider Landing an entry route to auto-redirect to dashboard.
    // For Platform Admin, we allow them to stay on Landing to explore public content.
    val isEntry = currentRoute.isAuthEntryRoute() || (currentRoute == Screen.Landing.route && role != UserRole.PLATFORM_ADMIN)
    return isEntry && navigateTo != "login" && !isNewRegistration
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

@Composable
fun SanibonaniNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()

     // Global session observer for forced navigation
     LaunchedEffect(authState.isLoggedIn, authState.userRole, authState.isNewRegistration, authState.navigateTo) {
         val currentRoute = navController.currentDestination?.route
         if (currentRoute == null) return@LaunchedEffect

         AppLogger.d(
             tag = "NavGraph",
             message = "sessionRouteCheck isLoggedIn=${authState.isLoggedIn}, role=${authState.userRole}, currentRoute=$currentRoute"
         )

         if (shouldRedirectForRoleMismatch(authState.isLoggedIn, authState.userRole, currentRoute)) {
             val fallback = destinationForUserRole(authState.userRole)
             AppLogger.d(
                 tag = "NavGraph",
                 message = "Role mismatch redirect role=${authState.userRole} from=$currentRoute to=$fallback"
             )
             navController.navigate(fallback) {
                 popUpTo(0) { inclusive = true }
             }
             return@LaunchedEffect
         }

          if (shouldForcePlatformAdminRedirect(authState.isLoggedIn, authState.userRole, currentRoute)) {
              AppLogger.d(tag = "NavGraph", message = "Redirecting platform admin to ${Screen.PlatformAdmin.route} from $currentRoute")
              navController.navigate(Screen.PlatformAdmin.route) {
                  popUpTo(0) { inclusive = true }
              }
              return@LaunchedEffect
          }

          if (authState.isLoggedIn) {
              if (shouldRedirectAuthenticatedFromEntry(currentRoute, authState.navigateTo, authState.isNewRegistration, authState.userRole)) {
                  val dest = destinationForUserRole(authState.userRole)
                  AppLogger.d(tag = "NavGraph", message = "Redirecting authenticated user to $dest")
                  navController.navigate(dest) {
                      popUpTo(0) { inclusive = true }
                  }
              } else if (authState.isNewRegistration && currentRoute == Screen.Register.route) {
                  // New registration: go to Landing page
                  navController.navigate(Screen.Landing.route) {
                      popUpTo(0) { inclusive = true }
                  }
                  authViewModel.clearNewRegistrationFlag()
              }
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
                onNavigateLogin = { redirect ->
                    if (redirect != null) {
                        navController.navigateProtected(redirect, authState.isLoggedIn)
                    } else {
                        navController.navigate(Screen.Login.route)
                    }
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
                onNavigatePlatformAdmin = {
                    navController.navigate(Screen.PlatformAdmin.route)
                },
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
                         AppLogger.d(tag = "NavGraph", message = "Applying protected redirect to $decodedRedirect")
                         navController.navigate(decodedRedirect) {
                             popUpTo(Screen.Login.route) { inclusive = true }
                         }
                     } else {
                          val destination = destinationForUserRole(role)
                         AppLogger.d(tag = "NavGraph", message = "Login callback routing to $destination for role=$role")
                         navController.navigate(destination) {
                             popUpTo(0) { inclusive = true }
                             launchSingleTop = true
                         }
                     }
                 },
                 onNavigateRegister = { navController.navigate(Screen.Register.route) },
                 onForgotPassword = { navController.navigate(Screen.PasswordRecovery.route) },
                 onBack = { navController.popBackStack() }
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
                navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val targetTab = backStackEntry.arguments?.getInt("targetTab") ?: 0
            val groupId = backStackEntry.arguments?.getString("groupId")
            val memberViewModel: MemberViewModel = hiltViewModel()

            // If a specific groupId is passed, ensure the ViewModel switches to it
            LaunchedEffect(groupId) {
                if (groupId != null) {
                    memberViewModel.switchGroup(groupId)
                }
            }

            MemberDashboardScreen(
                targetTab = targetTab,
                onNavigatePayment = { type, amount, gid ->
                    navController.navigate(Screen.Payment.build(type, amount, gid))
                },
                onNavigateAdmin = { navController.navigate(Screen.AdminDashboard.withId(null)) },
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
                navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { back ->
            val groupId = back.arguments?.getString("groupId")
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
                onNavigateToMemberPortal = { gid ->
                    navController.navigate(Screen.MemberDashboard.withTab(0, gid))
                },
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(Screen.Landing.route) { popUpTo(0) }
                },
                vm = adminViewModel
            )
        }

        composable(Screen.PlatformAdmin.route) {
            PlatformAdminScreen(
                onNavigateToCreateAdmin = { navController.navigate(Screen.CreatePlatformAdmin.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(Screen.Landing.route) { popUpTo(0) }
                }
            )
        }

        composable(Screen.CreatePlatformAdmin.route) {
            com.sanibonani.save.ui.screens.admin.CreatePlatformAdminScreen(
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
