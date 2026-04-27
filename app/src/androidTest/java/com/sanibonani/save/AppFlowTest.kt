package com.sanibonani.save

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Test for the main app flow starting from the Landing Screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testLandingScreenToBrowseGroups() {
        // GIVEN: The app starts. We wait for the Splash Screen to finish.
        // The SplashViewModel performs a connection check.
        composeTestRule.waitUntil(timeoutMillis = 20000) {
            composeTestRule.onAllNodesWithText("Savings Groups,\nUnified & Empowered").fetchSemanticsNodes().isNotEmpty()
        }
        
        // THEN: Verify the hero text is displayed
        composeTestRule.onNodeWithText("Savings Groups,\nUnified & Empowered").assertIsDisplayed()
        
        // WHEN: Clicking "Browse Groups" button
        composeTestRule.onNodeWithText("Browse Groups").performClick()
        
        // THEN: We should see the "Browse Groups" screen content
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun testLandingScreenToLogin() {
        // GIVEN: Wait for app to settle on Landing Screen
        composeTestRule.waitUntil(timeoutMillis = 20000) {
            composeTestRule.onAllNodesWithText("Already a member? Log in →").fetchSemanticsNodes().isNotEmpty()
        }
        
        // WHEN: Clicking "Already a member? Log in →"
        composeTestRule.onNodeWithText("Already a member? Log in →").performClick()
        
        // THEN: Verify Login screen is shown
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
    }
}
