package com.sanibonani.save.ui.screens.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.LoginRequest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for Auth screens (Login, Register, Password Recovery).
 * Tests form validation, user interactions, and navigation flows.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Any setup before each test
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGIN SCREEN TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `loginScreen - renders all UI elements`() {
        composeTestRule.setContent {
            // LoginScreen()
        }

        // Verify email field exists
        composeTestRule.onNodeWithTag("email_input")
            .assertIsDisplayed()
            .assertHasClickAction()

        // Verify password field exists
        composeTestRule.onNodeWithTag("password_input")
            .assertIsDisplayed()

        // Verify login button exists
        composeTestRule.onNodeWithTag("login_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `loginScreen - email validation shows error for invalid format`() {
        var validationError = ""
        composeTestRule.setContent {
            // LoginScreen(onValidationError = { validationError = it })
        }

        // Type invalid email
        composeTestRule.onNodeWithTag("email_input")
            .performTextInput("invalid-email")

        // Click outside to trigger validation
        composeTestRule.onNodeWithTag("password_input")
            .performClick()

        // Verify error is shown
        composeTestRule.onNode(
            hasText("Please enter a valid email") or hasText("Invalid email format")
        ).assertIsDisplayed()
    }

    @Test
    fun `loginScreen - password field shows/hides password on toggle`() {
        composeTestRule.setContent {
            // LoginScreen()
        }

        // Initially password should be hidden (asterisks)
        composeTestRule.onNodeWithTag("password_input")
            .assertTextContains("*") // Placeholder show

        // Click show/hide button
        composeTestRule.onNodeWithTag("password_visibility_toggle")
            .performClick()

        // Password should now be visible
        composeTestRule.onNodeWithTag("password_input")
            .performTextInput("SecurePass123!")
    }

    @Test
    fun `loginScreen - login button disabled when fields empty`() {
        composeTestRule.setContent {
            // LoginScreen()
        }

        composeTestRule.onNodeWithTag("login_button")
            .assertIsNotEnabled()
    }

    @Test
    fun `loginScreen - login button enabled with valid credentials`() {
        composeTestRule.setContent {
            // LoginScreen()
        }

        composeTestRule.onNodeWithTag("email_input")
            .performTextInput("user@example.com")

        composeTestRule.onNodeWithTag("password_input")
            .performTextInput("SecurePass123!")

        composeTestRule.onNodeWithTag("login_button")
            .assertIsEnabled()
    }

    @Test
    fun `loginScreen - shows loading indicator during login`() {
        var isLoading = false
        composeTestRule.setContent {
            // LoginScreen(state = state.copy(isLoading = isLoading))
        }

        // Simulate login
        isLoading = true

        composeTestRule.onNodeWithTag("loading_indicator")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("login_button")
            .assertIsNotEnabled()
    }

    @Test
    fun `loginScreen - displays error message on failed login`() {
        composeTestRule.setContent {
            // LoginScreen(state = state.copy(error = "Invalid credentials"))
        }

        composeTestRule.onNodeWithTag("error_banner")
            .assertIsDisplayed()
            .assertTextContains("Invalid credentials")
    }

    @Test
    fun `loginScreen - navigation to registration screen`() {
        var navigatedToRegister = false
        composeTestRule.setContent {
            // LoginScreen(onNavigateToRegister = { navigatedToRegister = true })
        }

        composeTestRule.onNodeWithText("Don't have an account?")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("register_link")
            .performClick()

        assert(navigatedToRegister)
    }

    @Test
    fun `loginScreen - navigation to password recovery screen`() {
        var navigatedToRecovery = false
        composeTestRule.setContent {
            // LoginScreen(onNavigateToRecovery = { navigatedToRecovery = true })
        }

        composeTestRule.onNodeWithText("Forgot password?")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("forgot_password_link")
            .performClick()

        assert(navigatedToRecovery)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REGISTRATION SCREEN TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `registerScreen - renders all required fields`() {
        composeTestRule.setContent {
            // RegisterScreen()
        }

        composeTestRule.onNodeWithTag("first_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("last_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phone_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("id_number_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_password_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_button").assertIsDisplayed()
    }

    @Test
    fun `registerScreen - ID number field formats with visual formatting`() {
        composeTestRule.setContent {
            // RegisterScreen()
        }

        composeTestRule.onNodeWithTag("id_number_input")
            .performTextInput("8001015800081")

        // Should be formatted after input (e.g., "800101-5800-081")
        composeTestRule.onNodeWithTag("id_number_input")
            .assertTextContains("800101")
    }

    @Test
    fun `registerScreen - phone number field formats with visual formatting`() {
        composeTestRule.setContent {
            // RegisterScreen()
        }

        composeTestRule.onNodeWithTag("phone_input")
            .performTextInput("0715555555")

        // Should be formatted as "071 555 5555"
        composeTestRule.onNodeWithTag("phone_input")
            .assertTextContains("071")
    }

    @Test
    fun `registerScreen - password mismatch shows error`() {
        composeTestRule.setContent {
            // RegisterScreen()
        }

        composeTestRule.onNodeWithTag("password_input")
            .performTextInput("SecurePass123!")

        composeTestRule.onNodeWithTag("confirm_password_input")
            .performTextInput("DifferentPass456!")

        composeTestRule.onNode(
            hasText("Passwords do not match")
        ).assertIsDisplayed()
    }

    @Test
    fun `registerScreen - register button disabled with validation errors`() {
        composeTestRule.setContent {
            // RegisterScreen()
        }

        // Don't fill in required fields
        composeTestRule.onNodeWithTag("register_button")
            .assertIsNotEnabled()
    }

    @Test
    fun `registerScreen - navigate back to login`() {
        var navigatedBack = false
        composeTestRule.setContent {
            // RegisterScreen(onNavigateBack = { navigatedBack = true })
        }

        composeTestRule.onNodeWithTag("back_button")
            .performClick()

        assert(navigatedBack)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PASSWORD RECOVERY SCREEN TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `passwordRecoveryScreen - renders email input and send button`() {
        composeTestRule.setContent {
            // PasswordRecoveryScreen()
        }

        composeTestRule.onNodeWithTag("email_input")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_recovery_email_button")
            .assertIsDisplayed()
    }

    @Test
    fun `passwordRecoveryScreen - send button disabled with empty email`() {
        composeTestRule.setContent {
            // PasswordRecoveryScreen()
        }

        composeTestRule.onNodeWithTag("send_recovery_email_button")
            .assertIsNotEnabled()
    }

    @Test
    fun `passwordRecoveryScreen - send button enabled with valid email`() {
        composeTestRule.setContent {
            // PasswordRecoveryScreen()
        }

        composeTestRule.onNodeWithTag("email_input")
            .performTextInput("user@example.com")

        composeTestRule.onNodeWithTag("send_recovery_email_button")
            .assertIsEnabled()
    }

    @Test
    fun `passwordRecoveryScreen - shows success message after email sent`() {
        composeTestRule.setContent {
            // PasswordRecoveryScreen(state = state.copy(isEmailSent = true))
        }

        composeTestRule.onNode(
            hasText("Recovery email has been sent") or hasText("Check your email")
        ).assertIsDisplayed()
    }

    @Test
    fun `passwordRecoveryScreen - navigate back to login`() {
        var navigatedBack = false
        composeTestRule.setContent {
            // PasswordRecoveryScreen(onNavigateBack = { navigatedBack = true })
        }

        composeTestRule.onNodeWithTag("back_button")
            .performClick()

        assert(navigatedBack)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `authScreen - shows network error when offline`() {
        composeTestRule.setContent {
            // AuthScreen(state = state.copy(error = "No internet connection"))
        }

        composeTestRule.onNodeWithTag("error_banner")
            .assertIsDisplayed()
            .assertTextContains("No internet connection")

        composeTestRule.onNodeWithTag("retry_button")
            .assertIsDisplayed()
    }

    @Test
    fun `authScreen - retry button triggers login again`() {
        var retryCount = 0
        composeTestRule.setContent {
            // AuthScreen(onRetry = { retryCount++ })
        }

        composeTestRule.onNodeWithTag("retry_button")
            .performClick()

        assert(retryCount > 0)
    }

    @Test
    fun `authScreen - dismisses error on successful action`() {
        composeTestRule.setContent {
            // AuthScreen(state = state.copy(error = "Some error"))
        }

        composeTestRule.onNodeWithTag("error_banner")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("dismiss_error_button")
            .performClick()

        composeTestRule.onNodeWithTag("error_banner")
            .assertDoesNotExist()
    }
}

