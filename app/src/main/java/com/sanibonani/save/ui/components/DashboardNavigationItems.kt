package com.sanibonani.save.ui.components


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

/**
 * Dashboard navigation descriptors.
 *
 * These are pure data builders: they take a tab-selection callback rather than a
 * feature ViewModel, so the shared component library stays independent of the
 * feature layer (it previously imported `AdminOverviewViewModel` /
 * `MemberOverviewViewModel` directly, which made shared UI depend on features).
 */

fun adminDashboardNavigationItems(
    onSelectTab: (Int) -> Unit
): List<NavigationItem> = listOf(
    NavigationItem("Overview", Icons.Default.Dashboard, "0", "Operations") { onSelectTab(0) },
    NavigationItem("Members", Icons.Default.Group, "1", "Operations") { onSelectTab(1) },
    NavigationItem("Alerts", Icons.Default.Notifications, "2", "Operations") { onSelectTab(2) },
    NavigationItem("Messaging", Icons.Default.Chat, "3", "Operations") { onSelectTab(3) },
    NavigationItem("Payouts", Icons.Default.Payments, "7", "Finance") { onSelectTab(7) },
    NavigationItem("Loans", Icons.Default.AccountBalance, "8", "Finance") { onSelectTab(8) },
    NavigationItem("Ledger", Icons.Default.List, "10", "Finance") { onSelectTab(10) },
    NavigationItem("Viability", Icons.Default.Insights, "4", "Finance") { onSelectTab(4) },
    NavigationItem("Audit", Icons.Default.Assessment, "11", "Oversight") { onSelectTab(11) },
    NavigationItem("Insights", Icons.Default.Analytics, "9", "Oversight") { onSelectTab(9) },
    NavigationItem("Settings", Icons.Default.Settings, "6", "Oversight") { onSelectTab(6) },
    NavigationItem("Account", Icons.Default.AccountCircle, "5", "Personal") { onSelectTab(5) }
)

fun memberDashboardNavigationItems(
    tabs: List<DashboardTabSpec>,
    onSelectTab: (Int) -> Unit
): List<NavigationItem> = listOf(
    NavigationItem("Overview", Icons.Default.Dashboard, "0", "Operations") { onSelectTab(0) },
    NavigationItem("Transactions", Icons.Default.History, "1", "Operations") { onSelectTab(1) },
    NavigationItem("Insights", Icons.Default.Insights, "2", "Operations") { onSelectTab(2) },
    NavigationItem("Loans", Icons.Default.AccountBalance, "3", "Finance") { onSelectTab(3) },
    NavigationItem("Audit", Icons.Default.Assessment, "4", "Finance") { onSelectTab(4) },
    NavigationItem(tabs[6].title, Icons.Default.Payments, "6", "Finance") { onSelectTab(6) },
    NavigationItem("Beneficiaries", Icons.Default.People, "5", "Personal") { onSelectTab(5) },
    NavigationItem("Documents", Icons.Default.Description, "7", "Personal") { onSelectTab(7) },
    NavigationItem("Profile", Icons.Default.Settings, "10", "Personal") { onSelectTab(10) },
    NavigationItem("Messages", Icons.Default.Chat, "8", "Messaging") { onSelectTab(8) },
    NavigationItem("Notifications", Icons.Default.Notifications, "9", "Messaging") { onSelectTab(9) }
)
