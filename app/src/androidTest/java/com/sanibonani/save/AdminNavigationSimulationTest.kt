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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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

    @Test
    fun testPlatformAdminNavigationFlow() {
        // 1. Wait for Splash or Content to appear
        composeTestRule.waitUntil(60000) {
            runCatching {
                composeTestRule.onAllNodesWithTag("branding_splash").fetchSemanticsNodes().isNotEmpty() ||
                    composeTestRule.onAllNodesWithTag("main_content").fetchSemanticsNodes().isNotEmpty() ||
                    composeTestRule.onAllNodesWithTag("connection_error_screen").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // 2. Wait for Dashboard, Landing, or Access Denied to load
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithText("Platform Administrator", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Sanibonani", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Access Denied", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Wait for Platform Admin screen specifically
        composeTestRule.waitUntil(60000) {
            composeTestRule.onAllNodesWithText("Platform Administrator", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Select Maintenance Tab
        composeTestRule.onNodeWithText("Maintenance", ignoreCase = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Maintenance", ignoreCase = true).assertIsSelected()
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText("Realtime System Status", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Expand Support Tools Accordion
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText("Support Tools", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Support Tools", ignoreCase = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 4. Trigger Impersonation
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Impersonate Group Admin", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Impersonate Group Admin", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()

        // 5. Select a group from the list
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("Admin navigation test group", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Admin navigation test group", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()

        // 6. Verify Support Dashboard
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText("Admin View (Support)", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 7. Verify core snapshot content
        composeTestRule.onNodeWithText("Group Snapshot", ignoreCase = true).assertIsDisplayed()
    }
}
