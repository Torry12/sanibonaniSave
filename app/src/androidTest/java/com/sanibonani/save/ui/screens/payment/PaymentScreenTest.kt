package com.sanibonani.save.ui.screens.payment

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for Payment screens.
 * Tests payment input validation, amount calculations, and payment method selection.
 */
@RunWith(AndroidJUnit4::class)
class PaymentScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMember = Member(
        id = "m1",
        userId = "user-1",
        groupId = "g1",
        firstName = "John",
        lastName = "Doe",
        status = MemberStatus.ACTIVE
    )

    private val testGroup = Group(
        id = "g1",
        name = "Test Burial Society",
        type = GroupType.BURIAL_SOCIETY,
        monthlyContribution = 150.0
    )

    private val testCalculation = PaymentCalculation(
        totalDueNow = 150.0,
        shortfall = 150.0,
        nextDueDate = "2024-02-28",
        isOverdue = true
    )

    @Before
    fun setUp() {
        // Setup before each test
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT SCREEN LAYOUT TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentScreen - renders all UI elements`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, group = testGroup, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("payment_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_display_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_method_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_button").assertIsDisplayed()
    }

    @Test
    fun `paymentScreen - shows payment title based on member status`() {
        val pendingMember = testMember.copy(status = MemberStatus.PENDING_PAYMENT)
        composeTestRule.setContent {
            // PaymentScreen(member = pendingMember)
        }

        composeTestRule.onNode(
            hasText("Pay Joining Fee") or hasText("Complete Registration")
        ).assertIsDisplayed()
    }

    @Test
    fun `paymentScreen - shows contribution title for active member`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("payment_title")
            .assertTextContains("Contribution")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AMOUNT DISPLAY TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentScreen - displays amount due`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_due_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_due_value")
            .assertIsDisplayed()
            .assertTextContains("R150.00")
    }

    @Test
    fun `paymentScreen - shows shortfall if behind`() {
        val behindCalculation = testCalculation.copy(
            shortfall = 300.0,
            totalDueNow = 450.0 // Includes late fee
        )
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = behindCalculation)
        }

        composeTestRule.onNodeWithTag("shortfall_banner")
            .assertIsDisplayed()
            .assertTextContains("300.00")
    }

    @Test
    fun `paymentScreen - shows late fee if overdue`() {
        val overdueCalculation = testCalculation.copy(isOverdue = true)
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = overdueCalculation)
        }

        composeTestRule.onNodeWithTag("late_fee_warning")
            .assertIsDisplayed()
    }

    @Test
    fun `paymentScreen - displays next due date`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("next_due_date")
            .assertIsDisplayed()
            .assertTextContains("2024-02-28")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AMOUNT INPUT TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `amountInput - accepts valid amount`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("150.00")

        composeTestRule.onNodeWithTag("amount_input_field")
            .assertTextContains("150.00")
    }

    @Test
    fun `amountInput - shows error for zero amount`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("0")

        composeTestRule.onNode(
            hasText("Amount must be greater than 0") or hasText("Invalid amount")
        ).assertIsDisplayed()
    }

    @Test
    fun `amountInput - shows error for negative amount`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("-100")

        composeTestRule.onNode(
            hasText("Amount must be positive")
        ).assertIsDisplayed()
    }

    @Test
    fun `amountInput - shows error for non-numeric input`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("abc")

        composeTestRule.onNode(
            hasText("Please enter a valid amount")
        ).assertIsDisplayed()
    }

    @Test
    fun `amountInput - auto-fills with amount due`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("use_suggested_amount_button")
            .performClick()

        composeTestRule.onNodeWithTag("amount_input_field")
            .assertTextContains("150.00")
    }

    @Test
    fun `amountInput - shows realtime calculation as user types`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("100")

        composeTestRule.onNodeWithTag("remaining_after_payment")
            .assertIsDisplayed()
            .assertTextContains("50.00") // 150 - 100 remaining
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT METHOD TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentMethodSelector - displays available methods`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("payment_method_yoco").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_method_bank_transfer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_method_wallet").assertIsDisplayed()
    }

    @Test
    fun `paymentMethodSelector - yoco method selected by default`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("payment_method_yoco")
            .assertIsSelected()
    }

    @Test
    fun `paymentMethodSelector - switching methods updates UI`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("payment_method_bank_transfer")
            .performClick()

        composeTestRule.onNodeWithTag("bank_transfer_details_section")
            .assertIsDisplayed()
    }

    @Test
    fun `paymentMethodSelector - yoco shows card details`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("payment_method_yoco")
            .performClick()

        composeTestRule.onNodeWithTag("card_details_section")
            .assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT BREAKDOWN TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentBreakdown - shows contribution amount`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("breakdown_contribution")
            .assertIsDisplayed()
            .assertTextContains("150.00")
    }

    @Test
    fun `paymentBreakdown - shows late fee if applicable`() {
        val withLateFee = testCalculation.copy(
            isOverdue = true,
            totalDueNow = 200.0 // 150 + 50 late fee
        )
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = withLateFee)
        }

        composeTestRule.onNodeWithTag("breakdown_late_fee")
            .assertIsDisplayed()
            .assertTextContains("50.00")
    }

    @Test
    fun `paymentBreakdown - shows total with all fees`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("breakdown_total")
            .assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONFIRMATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentButton - disabled with empty amount`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("payment_button")
            .assertIsNotEnabled()
    }

    @Test
    fun `paymentButton - enabled with valid amount`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("150")

        composeTestRule.onNodeWithTag("payment_button")
            .assertIsEnabled()
    }

    @Test
    fun `paymentButton - shows confirmation dialog on click`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("150")

        composeTestRule.onNodeWithTag("payment_button")
            .performClick()

        composeTestRule.onNodeWithTag("confirmation_dialog")
            .assertIsDisplayed()
    }

    @Test
    fun `confirmationDialog - displays payment summary`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("150")

        composeTestRule.onNodeWithTag("payment_button")
            .performClick()

        composeTestRule.onNodeWithTag("confirmation_amount")
            .assertIsDisplayed()
            .assertTextContains("150.00")
    }

    @Test
    fun `confirmationDialog - confirm button processes payment`() {
        var paymentProcessed = false
        composeTestRule.setContent {
            // PaymentScreen(onConfirmPayment = { paymentProcessed = true })
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("150")

        composeTestRule.onNodeWithTag("payment_button")
            .performClick()

        composeTestRule.onNodeWithTag("confirmation_confirm_button")
            .performClick()

        assert(paymentProcessed)
    }

    @Test
    fun `confirmationDialog - cancel button dismisses dialog`() {
        composeTestRule.setContent {
            // PaymentScreen(member = testMember, calculation = testCalculation)
        }

        composeTestRule.onNodeWithTag("amount_input_field")
            .performTextInput("150")

        composeTestRule.onNodeWithTag("payment_button")
            .performClick()

        composeTestRule.onNodeWithTag("confirmation_cancel_button")
            .performClick()

        composeTestRule.onNodeWithTag("confirmation_dialog")
            .assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT PROCESSING TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentProcessing - shows loading indicator`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(isLoading = true))
        }

        composeTestRule.onNodeWithTag("payment_loading_indicator")
            .assertIsDisplayed()
    }

    @Test
    fun `paymentProcessing - shows success message on completion`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(paymentStatus = PaymentStatus.SUCCESS))
        }

        composeTestRule.onNodeWithTag("payment_success_banner")
            .assertIsDisplayed()
            .assertTextContains("Payment successful")
    }

    @Test
    fun `paymentProcessing - shows error message on failure`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(error = "Card declined"))
        }

        composeTestRule.onNodeWithTag("payment_error_message")
            .assertIsDisplayed()
            .assertTextContains("Card declined")
    }

    @Test
    fun `paymentProcessing - retry button visible on error`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(error = "Payment failed"))
        }

        composeTestRule.onNodeWithTag("payment_retry_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `paymentProcessing - continue button visible on success`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(paymentStatus = PaymentStatus.SUCCESS))
        }

        composeTestRule.onNodeWithTag("payment_continue_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RECEIPT TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `paymentReceipt - displays after successful payment`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(paymentStatus = PaymentStatus.SUCCESS))
        }

        composeTestRule.onNodeWithTag("receipt_section")
            .assertIsDisplayed()
    }

    @Test
    fun `paymentReceipt - shows transaction details`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(paymentStatus = PaymentStatus.SUCCESS))
        }

        composeTestRule.onNodeWithTag("receipt_amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_date").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_reference").assertIsDisplayed()
    }

    @Test
    fun `paymentReceipt - download PDF button visible`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(paymentStatus = PaymentStatus.SUCCESS))
        }

        composeTestRule.onNodeWithTag("receipt_download_pdf_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `paymentReceipt - share receipt button visible`() {
        composeTestRule.setContent {
            // PaymentScreen(state = state.copy(paymentStatus = PaymentStatus.SUCCESS))
        }

        composeTestRule.onNodeWithTag("receipt_share_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}

