package com.sanibonani.save.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.features.financial.ui.PaymentSandboxScreen
import com.sanibonani.save.features.financial.ui.PaymentScreen

/** Payment checkout and its developer sandbox. */
internal fun NavGraphBuilder.financialRoutes(
    navController: NavHostController,
    onPaymentFailed: (Throwable) -> Unit,
) {
    composable(
        route = Screen.Payment.route,
        arguments = listOf(
            navArgument("type") { type = NavType.StringType },
            navArgument("amount") { type = NavType.StringType },
            navArgument("groupId") { type = NavType.StringType }
        )
    ) { back ->
        val type = back.requiredStringArg(navController, "type", "Payment") ?: return@composable
        val amountText = back.requiredStringArg(navController, "amount", "Payment") ?: return@composable
        val groupId = back.requiredStringArg(navController, "groupId", "Payment") ?: return@composable

        val amount = try {
            Money(java.math.BigDecimal(amountText))
        } catch (_: Exception) {
            null
        }
        if (amount == null || !amount.isPositive) {
            popBackWithError(navController, "[Payment] Invalid amount: $amountText. Popping back.")
            return@composable
        }

        PaymentScreen(
            paymentType = type,
            amount = amount,
            groupId = groupId,
            onPaymentComplete = {
                val destination = NavigationUtils.destinationForPaymentType(type, groupId)
                navController.navigateSingleTop(destination) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                }
            },
            onPaymentFailed = onPaymentFailed,
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.PaymentSandbox.route) {
        PaymentSandboxScreen(
            onBack = { navController.popBackStack() }
        )
    }
}
