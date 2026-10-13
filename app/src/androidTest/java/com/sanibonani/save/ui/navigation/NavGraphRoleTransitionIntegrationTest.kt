package com.sanibonani.save.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.MainActivity
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.UserRole
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavGraphRoleTransitionIntegrationTest {

    private companion object {
        const val TEST_MEMBER_EMAIL = "member.transition@example.com"
        const val TEST_MEMBER_PASSWORD = "Test@12345"
    }

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        TestAuthSessionController.reset(role = UserRole.MEMBER)
    }

    @Test
    fun memberToPlatformAdmin_roleTransition_redirectsToPlatformAdminRoute() {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Already a member? Log in →").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText("Already a member? Log in →").performClick()
        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()

        composeTestRule.onNodeWithText("Email Address").performTextInput(TEST_MEMBER_EMAIL)
        composeTestRule.onNodeWithText("Password").performTextInput(TEST_MEMBER_PASSWORD)
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Member Portal").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Member Portal").assertIsDisplayed()

        TestAuthSessionController.setRole(UserRole.PLATFORM_ADMIN)
        TestAuthSessionController.emitAuthenticatedSession()

        composeTestRule.waitUntil(timeoutMillis = 25_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("Platform Administration", substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Platform Administration", substring = true).assertIsDisplayed()
    }
}

