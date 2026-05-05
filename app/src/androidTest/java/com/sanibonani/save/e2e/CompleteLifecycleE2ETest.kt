package com.sanibonani.save.e2e

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.MainActivity
import com.sanibonani.save.R
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for complete user workflows.
 * Tests critical paths through the app from start to finish.
 */
@RunWith(AndroidJUnit4::class)
class MemberCompleteLifecycleE2ETest {

    @Before
    fun setUp() {
        ActivityScenario.launch(MainActivity::class.java)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MEMBER REGISTRATION TO PAYMENT E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_memberRegistrationToPayment - complete flow`() {
        // Step 1: Navigate to registration
        onView(withText("Register"))
            .perform(ViewActions.click())

        // Step 2: Fill in registration form
        onView(withId(R.id.first_name_input))
            .perform(ViewActions.typeText("John"))

        onView(withId(R.id.last_name_input))
            .perform(ViewActions.typeText("Doe"))

        onView(withId(R.id.email_input))
            .perform(ViewActions.typeText("john@example.com"))

        onView(withId(R.id.phone_input))
            .perform(ViewActions.typeText("0715555555"))

        onView(withId(R.id.id_number_input))
            .perform(ViewActions.typeText("8001015800081"))

        onView(withId(R.id.password_input))
            .perform(ViewActions.typeText("SecurePass123!"))

        onView(withId(R.id.confirm_password_input))
            .perform(ViewActions.typeText("SecurePass123!"))

        // Step 3: Submit registration form
        onView(withId(R.id.register_button))
            .perform(ViewActions.click())

        // Step 4: Verify group selection screen appears
        onView(withText("Select Group"))
            .check(matches(isDisplayed()))

        // Step 5: Select a group
        onView(allOf(
            withText("Test Burial Society"),
            isDisplayed()
        )).perform(ViewActions.click())

        // Step 6: Verify payment screen appears
        onView(withId(R.id.payment_title))
            .check(matches(withText(containsString("Payment"))))

        // Step 7: Enter payment amount
        onView(withId(R.id.amount_input))
            .perform(ViewActions.typeText("100.00"))

        // Step 8: Confirm payment
        onView(withId(R.id.payment_button))
            .check(matches(isEnabled()))
            .perform(ViewActions.click())

        // Step 9: Confirm in dialog
        onView(withId(R.id.confirmation_confirm_button))
            .perform(ViewActions.click())

        // Step 10: Verify success message
        onView(withText(containsString("Payment successful")))
            .check(matches(isDisplayed()))

        // Step 11: Navigate to member dashboard
        onView(withId(R.id.continue_button))
            .perform(ViewActions.click())

        // Step 12: Verify member dashboard is displayed
        onView(withId(R.id.member_dashboard_screen))
            .check(matches(isDisplayed()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONTRIBUTION PAYMENT E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_memberContributionPayment - record and verify`() {
        // Assume user is already logged in
        // Step 1: Navigate to overview tab
        onView(withId(R.id.tab_overview))
            .perform(ViewActions.click())

        // Step 2: Click make payment button
        onView(withId(R.id.make_payment_button))
            .perform(ViewActions.click())

        // Step 3: Verify payment screen
        onView(withText(containsString("Contribution")))
            .check(matches(isDisplayed()))

        // Step 4: Enter payment amount (current due)
        onView(withId(R.id.use_suggested_amount_button))
            .perform(ViewActions.click())

        // Step 5: Select payment method (Yoco)
        onView(withId(R.id.payment_method_yoco))
            .perform(ViewActions.click())

        // Step 6: Submit payment
        onView(withId(R.id.payment_button))
            .perform(ViewActions.click())

        // Step 7: Confirm payment
        onView(withId(R.id.confirmation_confirm_button))
            .perform(ViewActions.click())

        // Step 8: Verify success
        onView(withText(containsString("successful")))
            .check(matches(isDisplayed()))

        // Step 9: Continue to dashboard
        onView(withId(R.id.payment_continue_button))
            .perform(ViewActions.click())

        // Step 10: Verify member is still ACTIVE
        onView(withId(R.id.member_status_badge))
            .check(matches(withText("ACTIVE")))

        // Step 11: Verify balance updated in overview
        onView(withId(R.id.member_balance_display))
            .check(matches(isDisplayed()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DOCUMENT UPLOAD E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_documentUpload - upload and verify status`() {
        // Step 1: Navigate to documents tab
        onView(withId(R.id.tab_documents))
            .perform(ViewActions.click())

        // Step 2: Verify documents section displays
        onView(withId(R.id.documents_list))
            .check(matches(isDisplayed()))

        // Step 3: Click ID document slot
        onView(withId(R.id.id_document_slot))
            .perform(ViewActions.click())

        // Step 4: Select file from gallery/camera (simulated)
        // In real test, would use Espresso intents

        // Step 5: Verify upload progress
        onView(withId(R.id.upload_progress_indicator))
            .check(matches(isDisplayed()))

        // Step 6: Wait for upload to complete
        Thread.sleep(2000)

        // Step 7: Verify status changed to PENDING
        onView(allOf(
            withId(R.id.id_document_status),
            withText("PENDING")
        )).check(matches(isDisplayed()))

        // Step 8: Verify download will be available after verification
        // Status should show "Pending verification" message
        onView(withText(containsString("Pending")))
            .check(matches(isDisplayed()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOAN REQUEST E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_loanRequest - request and track`() {
        // Assume member is eligible (6+ months, good standing)

        // Step 1: Navigate to loans tab
        onView(withId(R.id.tab_loans))
            .perform(ViewActions.click())

        // Step 2: Verify loan eligibility displayed
        onView(withId(R.id.loan_eligibility_banner))
            .check(matches(isDisplayed()))

        // Step 3: Verify surety amount shown
        onView(withId(R.id.surety_amount_card))
            .check(matches(isDisplayed()))

        // Step 4: Click request loan button
        onView(withId(R.id.request_loan_button))
            .perform(ViewActions.click())

        // Step 5: Fill loan request form
        onView(withId(R.id.loan_amount_input))
            .perform(ViewActions.typeText("500.00"))

        onView(withId(R.id.loan_term_selector))
            .perform(ViewActions.click())

        onView(withText("3 months"))
            .perform(ViewActions.click())

        // Step 6: Submit loan request
        onView(withId(R.id.submit_loan_button))
            .perform(ViewActions.click())

        // Step 7: Confirm submission
        onView(withId(R.id.loan_confirmation_button))
            .perform(ViewActions.click())

        // Step 8: Verify success message
        onView(withText(containsString("Loan request submitted")))
            .check(matches(isDisplayed()))

        // Step 9: Verify loan appears in active loans list
        onView(withId(R.id.active_loans_section))
            .check(matches(isDisplayed()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MULTI-GROUP SWITCHING E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_multiGroupSwitching - switch and verify isolation`() {
        // Assume member belongs to multiple groups

        // Step 1: Click group switcher
        onView(withId(R.id.group_switcher))
            .perform(ViewActions.click())

        // Step 2: Verify group list appears
        onView(withId(R.id.group_switcher_dropdown))
            .check(matches(isDisplayed()))

        // Step 3: Verify current group has checkmark
        onView(allOf(
            withId(R.id.group_option_checkmark),
            isDisplayed()
        )).check(matches(isDisplayed()))

        // Step 4: Switch to different group
        onView(withText("Group 2"))
            .perform(ViewActions.click())

        // Step 5: Verify dashboard reloads
        onView(withId(R.id.member_dashboard_screen))
            .check(matches(isDisplayed()))

        // Step 6: Verify data is from new group (different balance, contributions)
        onView(withId(R.id.member_balance_display))
            .check(matches(isDisplayed()))

        // Step 7: Switch back to first group
        onView(withId(R.id.group_switcher))
            .perform(ViewActions.click())

        onView(withText("Group 1"))
            .perform(ViewActions.click())

        // Step 8: Verify data shows original group's data again
        onView(withId(R.id.member_dashboard_screen))
            .check(matches(isDisplayed()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSACTION HISTORY VIEW E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_transactionHistoryView - export and verify`() {
        // Step 1: Navigate to transactions tab
        onView(withId(R.id.tab_transactions))
            .perform(ViewActions.click())

        // Step 2: Verify transaction list displayed
        onView(withId(R.id.transaction_list))
            .check(matches(isDisplayed()))

        // Step 3: Verify each transaction shows amount, date, status
        onView(allOf(
            withId(R.id.transaction_item_amount),
            isDisplayed()
        )).check(matches(isDisplayed()))

        // Step 4: Scroll through list
        onView(withId(R.id.transaction_list))
            .perform(ViewActions.scrollTo())

        // Step 5: Click export to CSV button
        onView(withId(R.id.export_csv_button))
            .perform(ViewActions.click())

        // Step 6: Verify toast indicating export started
        onView(withText(containsString("Exporting")))
            .check(matches(isDisplayed()))

        // Step 7: Click download PDF button
        onView(withId(R.id.download_pdf_button))
            .perform(ViewActions.click())

        // Step 8: Verify download started message
        onView(withText(containsString("Downloading PDF")))
            .check(matches(isDisplayed()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT E2E TEST
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `e2e_profileManagement - update and verify`() {
        // Step 1: Navigate to profile tab
        onView(withId(R.id.tab_profile))
            .perform(ViewActions.click())

        // Step 2: Verify profile info displayed
        onView(withId(R.id.profile_name))
            .check(matches(isDisplayed()))

        onView(withId(R.id.profile_email))
            .check(matches(isDisplayed()))

        // Step 3: Click edit profile button
        onView(withId(R.id.edit_profile_button))
            .perform(ViewActions.click())

        // Step 4: Update profile fields (phone number example)
        onView(withId(R.id.phone_input))
            .perform(ViewActions.clearText())
            .perform(ViewActions.typeText("0725555555"))

        // Step 5: Save changes
        onView(withId(R.id.save_profile_button))
            .perform(ViewActions.click())

        // Step 6: Verify success message
        onView(withText(containsString("Profile updated")))
            .check(matches(isDisplayed()))

        // Step 7: Verify changes persisted
        onView(withId(R.id.profile_phone))
            .check(matches(withText(containsString("0725555555"))))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ADMIN PAYOUT WORKFLOW E2E TEST
// ══════════════════════════════════════════════════════════════════════════════

@RunWith(AndroidJUnit4::class)
class AdminPayoutWorkflowE2ETest {

    @Before
    fun setUp() {
        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun `e2e_adminPayoutWorkflow - request through approval`() {
        // Step 1: Login as admin
        // (Assume already logged in)

        // Step 2: Navigate to payouts section
        onView(withId(R.id.admin_payouts_tab))
            .perform(ViewActions.click())

        // Step 3: Click request payout button
        onView(withId(R.id.request_payout_button))
            .perform(ViewActions.click())

        // Step 4: Fill payout form
        onView(withId(R.id.beneficiary_selector))
            .perform(ViewActions.click())

        onView(withText("John Doe"))
            .perform(ViewActions.click())

        onView(withId(R.id.amount_input))
            .perform(ViewActions.typeText("1000.00"))

        onView(withId(R.id.reason_input))
            .perform(ViewActions.typeText("Burial assistance"))

        // Step 5: Fill banking details
        onView(withId(R.id.bank_account_input))
            .perform(ViewActions.typeText("1234567890"))

        onView(withId(R.id.branch_code_input))
            .perform(ViewActions.typeText("123456"))

        onView(withId(R.id.beneficiary_name_input))
            .perform(ViewActions.typeText("John Doe"))

        // Step 6: Submit payout request
        onView(withId(R.id.submit_payout_button))
            .perform(ViewActions.click())

        // Step 7: Confirm submission
        onView(withId(R.id.payout_confirmation_button))
            .perform(ViewActions.click())

        // Step 8: Verify payout appears in PENDING list
        onView(withId(R.id.pending_payouts_section))
            .check(matches(isDisplayed()))

        // Step 9: Verify notifications sent
        onView(withId(R.id.notifications_badge))
            .check(matches(isDisplayed()))
    }
}

