package com.sanibonani.save

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Robust navigation simulation for the Admin Dashboard.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdminNavigationSimulationTest {

    @Inject lateinit var groupRepo: GroupRepository

    @Inject lateinit var platformRepository: PlatformRepository

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        // Force authenticate as Platform Admin
        TestAuthSessionController.reset(UserRole.PLATFORM_ADMIN)
        TestAuthSessionController.signIn("torrymsimango@gmail.com")
        runBlocking {
            groupRepo.createGroup(
                Group(
                    id = UUID.randomUUID().toString(),
                    name = "Admin navigation test group",
                    type = GroupType.STOKVEL,
                    adminUserId = TestAuthSessionController.mockedUserId,
                    registrationPaid = true
                )
            ).getOrThrow()
        }
        composeTestRule.activityRule.scenario.recreate()
    }

    /**
     * waitUntil whose predicate survives the window setup() creates: after recreate() the old
     * composition is gone and the new one is not yet frame-ready, and fetchSemanticsNodes() throws
     * "No compose hierarchies found in the app" there instead of reporting no nodes.
     */
    private fun awaitNode(timeoutMs: Long = 30_000, matcher: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMs) { runCatching(matcher).getOrDefault(false) }
    }

    @Test
    fun testPlatformAdminNavigationFlow() {
        // 1. Wait for Splash or Content to appear
        awaitNode(60_000) {
            composeTestRule.onAllNodesWithTag("branding_splash").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithTag("main_content").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithTag("connection_error_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Wait for Dashboard, Landing, or Access Denied to load
        awaitNode(60_000) {
            composeTestRule.onAllNodesWithText("Platform Administrator", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Sanibonani", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Access Denied", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Wait for Platform Admin screen specifically
        awaitNode(60_000) {
            composeTestRule.onAllNodesWithText("Platform Administrator", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Select Maintenance Tab
        awaitNode {
            composeTestRule.onAllNodesWithText("Maintenance", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Maintenance", ignoreCase = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Maintenance", ignoreCase = true).assertIsSelected()
        awaitNode {
            composeTestRule.onAllNodesWithText("Realtime System Status", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Expand Support Tools Accordion
        awaitNode {
            composeTestRule.onAllNodesWithText("Support Tools", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Support Tools", ignoreCase = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 4. Trigger Impersonation
        awaitNode(20_000) {
            composeTestRule.onAllNodesWithText("Impersonate Group Admin", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Expanding "Support Tools" pushes this button below the fold, where a bare performClick
        // injects a touch that hits nothing.
        composeTestRule.onNodeWithText("Impersonate Group Admin", ignoreCase = true)
            .performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 5. The roster read, the dialog and the row are checked apart: one timeout on the group
        // name cannot say which of the three never arrived.
        val rosterRead = runBlocking { platformRepository.getAllGroups() }
        val roster = rosterRead.getOrElse { emptyList() }
        assertTrue(
            "The platform roster read did not return the group created in setup. " +
                "Rows: ${roster.map { it.name }} Refusal: ${rosterRead.exceptionOrNull()?.message}",
            roster.any { it.name == "Admin navigation test group" }
        )

        awaitNode(10_000) {
            composeTestRule.onAllNodesWithText("Select Group", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        awaitNode(20_000) {
            composeTestRule.onAllNodesWithText("Admin navigation test group", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Admin navigation test group", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()

        // 6. Verify Support Dashboard
        awaitNode {
            composeTestRule.onAllNodesWithText("Admin View (Support)", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 7. Verify core snapshot content
        composeTestRule.onNodeWithText("Group Snapshot", ignoreCase = true).assertIsDisplayed()
    }
}
