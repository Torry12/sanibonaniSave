package com.sanibonani.save

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.di.TestDataStore
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.UserRole
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Test for the main app flow starting from the Landing Screen.
 * Verified: Landing -> Browse Groups, Landing -> Login.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        TestAuthSessionController.reset(UserRole.GROUP_ADMIN)
        TestDataStore.reset()
        composeTestRule.activityRule.scenario.recreate()
    }

    private fun waitForText(text: String) {
        // On a cold start the Compose hierarchy may not exist yet, and fetching semantics
        // from it throws rather than returning empty.
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            runCatching {
                composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun testLandingScreenToBrowseGroups() {
        // Wait for splash to finish and landing screen to appear
        waitForText("Browse Groups")

        // Click on "Browse Groups" (Hero action for guest)
        composeTestRule.onNodeWithText("Browse Groups").performClick()

        // Verify we are on Browse Groups screen by checking for "Discover Groups" title in TopBar
        composeTestRule.onNodeWithText("Discover Groups").assertIsDisplayed()

        // Verify search bar is present
        composeTestRule.onNodeWithText("Search groups...").assertIsDisplayed()
    }

    @Test
    fun testLandingScreenToLogin() {
        // Wait for landing screen
        waitForText("Already a member? Log in")

        // Click on login text
        composeTestRule.onNodeWithText("Already a member? Log in").performClick()

        // Verify we are on Login screen
        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email Address").assertIsDisplayed()
    }
}
