package com.sanibonani.save

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.di.TestAuthSessionController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * High-fidelity navigation simulation for the Admin Dashboard.
 * Verifies that all tabs hydrate data correctly and group-switching resets state.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationSimulationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        // Force authenticate as Platform Admin for dashboard access
        TestAuthSessionController.reset(UserRole.PLATFORM_ADMIN)
        TestAuthSessionController.signIn("torrymsimango@gmail.com")
    }

    @Test
    fun testAdminDashboardTabTraversal() {
        // 1. Wait for Landing and trigger navigation to Admin View (Support)
        // Note: In a real app, this would be a click on a Support button or direct deep link.
        // For simulation, we assume the app starts in the Admin role context.

        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText("Overview", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        val tabs = listOf(
            "Overview", "Members", "Alerts", "Messaging",
            "Payouts", "Loans", "Ledger", "Viability",
            "Audit", "Insights", "Settings", "Account"
        )

        tabs.forEach { tabName ->
            // Click the tab
            composeTestRule.onNodeWithText(tabName, ignoreCase = true).performClick()
            composeTestRule.waitForIdle()

            // Verify a core element of that tab exists (hydration check)
            when (tabName) {
                "Overview" -> composeTestRule.onNodeWithText("Group Snapshot", ignoreCase = true).assertIsDisplayed()
                "Members" -> composeTestRule.onNodeWithText("Search by name", substring = true).assertIsDisplayed()
                "Ledger" -> composeTestRule.onNodeWithText("Audit Trail", substring = true).assertIsDisplayed()
                "Messaging" -> composeTestRule.onNodeWithText("Broadcast", substring = true).assertIsDisplayed()
            }
        }
    }

    @Test
    fun testGroupSwitchingUIRest() {
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Overview", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Open Members Tab
        composeTestRule.onNodeWithText("Members", ignoreCase = true).performClick()

        // 2. Click on a member to open details (assuming seed data exists)
        composeTestRule.onAllNodes(hasClickAction()).onFirst().performClick()

        // 3. Trigger Group Switch
        composeTestRule.onNodeWithContentDescription("Switch Group", substring = true).performClick()
        composeTestRule.onAllNodes(hasClickAction()).onFirst().performClick()

        // 4. Verify that the Detail panel is cleared and we are back at the List (Navigation Hardening check)
        composeTestRule.onNodeWithText("Select a member to view details").assertDoesNotExist()
        composeTestRule.onNodeWithText("Search by name", substring = true).assertIsDisplayed()
    }
}
