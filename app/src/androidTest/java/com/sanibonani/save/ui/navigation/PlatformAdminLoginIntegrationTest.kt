package com.sanibonani.save.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.MainActivity
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlatformAdminLoginIntegrationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Start from non-admin to verify credentials alone trigger platform-admin routing.
        TestAuthSessionController.reset(role = UserRole.MEMBER)
    }

    @Test
    fun platformAdminLogin_withWrongPassword_showsInvalidCredentialsAndStaysOnLogin() {
        goToLogin()

        composeTestRule.onNodeWithText("Email Address").performTextClearance()
        composeTestRule.onNodeWithText("Email Address").performTextInput(PlatformAdminAuthPolicy.EMAIL)
        composeTestRule.onNodeWithText("Password").performTextClearance()
        composeTestRule.onNodeWithText("Password").performTextInput("wrong-password")
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Invalid email or password", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid email or password", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun platformAdminLogin_withValidCredentials_navigatesToPlatformAdminPortal() {
        goToLogin()

        composeTestRule.onNodeWithText("Email Address").performTextClearance()
        composeTestRule.onNodeWithText("Email Address").performTextInput(PlatformAdminAuthPolicy.EMAIL)
        composeTestRule.onNodeWithText("Password").performTextClearance()
        composeTestRule.onNodeWithText("Password").performTextInput(PlatformAdminAuthPolicy.PASSWORD)
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            TestAuthSessionController.mockedRole == UserRole.PLATFORM_ADMIN &&
                TestAuthSessionController.mockedUserId != null
        }

        composeTestRule.waitUntil(timeoutMillis = 25_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Platform Administration", substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText("Platform Administration", substring = true).assertIsDisplayed()
    }

    @Test
    fun platformAdminLogin_withTrimmedEmail_navigatesToPlatformAdminPortal() {
        goToLogin()

        composeTestRule.onNodeWithText("Email Address").performTextClearance()
        composeTestRule.onNodeWithText("Email Address").performTextInput("  ${PlatformAdminAuthPolicy.EMAIL}  ")
        composeTestRule.onNodeWithText("Password").performTextClearance()
        composeTestRule.onNodeWithText("Password").performTextInput(PlatformAdminAuthPolicy.PASSWORD)
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            TestAuthSessionController.mockedRole == UserRole.PLATFORM_ADMIN &&
                TestAuthSessionController.mockedUserId != null
        }

        composeTestRule.waitUntil(timeoutMillis = 25_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Platform Administration", substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText("Platform Administration", substring = true).assertIsDisplayed()
    }

    @Test
    fun platformAdminLogin_withAliasPassword_navigatesToPlatformAdminPortal() {
        goToLogin()

        composeTestRule.onNodeWithText("Email Address").performTextClearance()
        composeTestRule.onNodeWithText("Email Address").performTextInput(PlatformAdminAuthPolicy.EMAIL)
        composeTestRule.onNodeWithText("Password").performTextClearance()
        composeTestRule.onNodeWithText("Password").performTextInput("ttor123M")
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            TestAuthSessionController.mockedRole == UserRole.PLATFORM_ADMIN &&
                TestAuthSessionController.mockedUserId != null
        }

        composeTestRule.waitUntil(timeoutMillis = 25_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Platform Administration", substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText("Platform Administration", substring = true).assertIsDisplayed()
    }

    private fun goToLogin() {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Already a member? Log in →").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Already a member? Log in →").performClick()
        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
    }
}
