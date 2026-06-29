package com.sanibonani.save.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

    private companion object {
        const val TEST_PLATFORM_ADMIN_PASSWORD = "torry123M"
    }

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Start from non-admin to verify credentials alone trigger platform-admin routing.
        // Note: do NOT call recreate() here — it detaches the ComposeTestRule from the new
        // activity and causes waitUntil to poll a stale composition.
        // Instead, reset the mock session and let the reactive NavGraph redirect settle.
        TestAuthSessionController.reset(role = UserRole.MEMBER)
        // Allow the session-reset signal to propagate through the Compose tree before
        // the test body starts probing for UI nodes.
        composeTestRule.waitForIdle()
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

        composeTestRule.onNodeWithText("Platform Admin Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid email or password", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun platformAdminLogin_withValidCredentials_navigatesToPlatformAdminPortal() {
        goToLogin()

        composeTestRule.onNodeWithText("Email Address").performTextClearance()
        composeTestRule.onNodeWithText("Email Address").performTextInput(PlatformAdminAuthPolicy.EMAIL)
        composeTestRule.onNodeWithText("Password").performTextClearance()
        composeTestRule.onNodeWithText("Password").performTextInput(TEST_PLATFORM_ADMIN_PASSWORD)
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            TestAuthSessionController.mockedRole == UserRole.PLATFORM_ADMIN &&
                TestAuthSessionController.mockedUserId != null
        }

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            isPlatformAdminPortalVisible()
        }

        assertPlatformAdminPortalVisible()
    }

    @Test
    fun platformAdminLogin_withTrimmedEmail_navigatesToPlatformAdminPortal() {
        goToLogin()

        composeTestRule.onNodeWithText("Email Address").performTextClearance()
        composeTestRule.onNodeWithText("Email Address").performTextInput("  ${PlatformAdminAuthPolicy.EMAIL}  ")
        composeTestRule.onNodeWithText("Password").performTextClearance()
        composeTestRule.onNodeWithText("Password").performTextInput(TEST_PLATFORM_ADMIN_PASSWORD)
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            TestAuthSessionController.mockedRole == UserRole.PLATFORM_ADMIN &&
                TestAuthSessionController.mockedUserId != null
        }

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            isPlatformAdminPortalVisible()
        }

        assertPlatformAdminPortalVisible()
    }

    private fun goToLogin() {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            // We can start on landing or already be on login depending on prior auth state.
            val onLogin = hasNodeWithText("Welcome Back") ||
                hasNodeWithText("Platform Admin Login") ||
                hasNodeWithText("Email Address")
            if (onLogin) return@waitUntil true

            // Use the direct login CTA to avoid protected-route redirect parameters.
            if (hasNodeWithText("Already have an account? Log In", substring = true)) {
                composeTestRule.onNodeWithText("Already have an account? Log In", substring = true).performClick()
            }
            false
        }

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            hasNodeWithText("Welcome Back") || hasNodeWithText("Platform Admin Login") || hasNodeWithText("Email Address")
        }
    }

    private fun hasNodeWithText(text: String, substring: Boolean = false): Boolean {
        return runCatching {
            composeTestRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    }

    private fun isPlatformAdminPortalVisible(): Boolean {
        return hasNodeWithText("Platform Administration", substring = true) ||
            hasNodeWithText("Platform Analytics") ||
            hasNodeWithText("All Groups") ||
            hasNodeWithText("Fee Management")
    }

    private fun assertPlatformAdminPortalVisible() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) { isPlatformAdminPortalVisible() }
    }
}
