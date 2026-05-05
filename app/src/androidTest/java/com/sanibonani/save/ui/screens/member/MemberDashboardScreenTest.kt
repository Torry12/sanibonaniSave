package com.sanibonani.save.ui.screens.member

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * UI tests for Member Dashboard and related screens.
 * Tests tab navigation, data display, actions, and multi-group switching.
 */
@RunWith(AndroidJUnit4::class)
class MemberDashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMember = Member(
        id = "m1",
        userId = "user-1",
        groupId = "g1",
        firstName = "John",
        lastName = "Doe",
        email = "john@example.com",
        phoneNumber = "0715555555",
        status = MemberStatus.ACTIVE,
        joinedAt = "2024-01-01T00:00:00Z"
    )

    private val testGroup = Group(
        id = "g1",
        name = "Test Burial Society",
        type = GroupType.BURIAL_SOCIETY,
        monthlyContribution = 150.0,
        currentMembers = 45,
        currentBalance = 15000.0
    )

    @Before
    fun setUp() {
        // Setup before each test
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DASHBOARD LAYOUT TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `memberDashboard - renders all 8 tabs`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("tab_overview").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_loans").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_beneficiaries").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_documents").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_messages").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_notifications").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_profile").assertIsDisplayed()
    }

    @Test
    fun `memberDashboard - displays group switcher for multi-group member`() {
        val multiGroupMember = testMember.copy(isMultiGroupMember = true)
        composeTestRule.setContent {
            // MemberDashboardScreen(member = multiGroupMember)
        }

        composeTestRule.onNodeWithTag("group_switcher")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `memberDashboard - does not display group switcher for single group member`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("group_switcher")
            .assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OVERVIEW TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `overviewTab - displays member status`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("member_status_badge")
            .assertIsDisplayed()
            .assertTextContains("ACTIVE")
    }

    @Test
    fun `overviewTab - displays payment status`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("payment_status_card")
            .assertIsDisplayed()
    }

    @Test
    fun `overviewTab - shows payment amount due`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember, calculation = calculation)
        }

        composeTestRule.onNodeWithTag("amount_due_display")
            .assertIsDisplayed()
    }

    @Test
    fun `overviewTab - payment button enabled for overdue member`() {
        val overdueCalculation = PaymentCalculation(
            totalDueNow = 150.0,
            shortfall = 150.0,
            isOverdue = true
        )
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember, calculation = overdueCalculation)
        }

        composeTestRule.onNodeWithTag("make_payment_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `overviewTab - shows recent activity list`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember)
        }

        composeTestRule.onNodeWithTag("recent_activity_section")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("activity_list")
            .assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSACTIONS TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `transactionsTab - displays contribution history`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.TRANSACTIONS)
        }

        composeTestRule.onNodeWithTag("transaction_list")
            .assertIsDisplayed()
    }

    @Test
    fun `transactionsTab - shows export to CSV button`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.TRANSACTIONS)
        }

        composeTestRule.onNodeWithTag("export_csv_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `transactionsTab - shows download PDF button`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.TRANSACTIONS)
        }

        composeTestRule.onNodeWithTag("download_pdf_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `transactionsTab - export CSV triggers download`() {
        var csvExported = false
        composeTestRule.setContent {
            // MemberDashboardScreen(onExportCSV = { csvExported = true })
        }

        composeTestRule.onNodeWithTag("export_csv_button")
            .performClick()

        assert(csvExported)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOANS TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `loansTab - displays loan eligibility status`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.LOANS)
        }

        composeTestRule.onNodeWithTag("loan_eligibility_banner")
            .assertIsDisplayed()
    }

    @Test
    fun `loansTab - shows request loan button when eligible`() {
        val eligibleMember = testMember.copy(
            joinedAt = "2023-01-01T00:00:00Z", // Over 6 months
            status = MemberStatus.ACTIVE
        )
        composeTestRule.setContent {
            // MemberDashboardScreen(member = eligibleMember, currentTab = MemberTabIndex.LOANS)
        }

        composeTestRule.onNodeWithTag("request_loan_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `loansTab - disables request loan button when ineligible`() {
        val newMember = testMember.copy(
            joinedAt = LocalDate.now().toString() + "T00:00:00Z" // Just joined
        )
        composeTestRule.setContent {
            // MemberDashboardScreen(member = newMember, currentTab = MemberTabIndex.LOANS)
        }

        composeTestRule.onNodeWithTag("request_loan_button")
            .assertIsNotEnabled()
    }

    @Test
    fun `loansTab - displays active loans list`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.LOANS)
        }

        composeTestRule.onNodeWithTag("active_loans_section")
            .assertIsDisplayed()
    }

    @Test
    fun `loansTab - shows surety amount visually`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.LOANS)
        }

        composeTestRule.onNodeWithTag("surety_amount_card")
            .assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BENEFICIARIES TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `beneficiariesTab - displays beneficiary list`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.BENEFICIARIES)
        }

        composeTestRule.onNodeWithTag("beneficiary_list")
            .assertIsDisplayed()
    }

    @Test
    fun `beneficiariesTab - add beneficiary button visible`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.BENEFICIARIES)
        }

        composeTestRule.onNodeWithTag("add_beneficiary_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `beneficiariesTab - shows max beneficiaries indicator`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.BENEFICIARIES)
        }

        composeTestRule.onNodeWithTag("beneficiary_count_indicator")
            .assertIsDisplayed()
            .assertTextContains("of 5") // Max beneficiaries = 5
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DOCUMENTS TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `documentsTab - displays 5 document upload slots`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.DOCUMENTS)
        }

        composeTestRule.onNodeWithTag("id_document_slot").assertIsDisplayed()
        composeTestRule.onNodeWithTag("por_document_slot").assertIsDisplayed()
        composeTestRule.onNodeWithTag("beneficiary_form_slot").assertIsDisplayed()
        composeTestRule.onNodeWithTag("marriage_cert_slot").assertIsDisplayed()
        composeTestRule.onNodeWithTag("constitution_slot").assertIsDisplayed()
    }

    @Test
    fun `documentsTab - shows upload button for pending documents`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.DOCUMENTS)
        }

        composeTestRule.onNodeWithTag("id_document_slot")
            .onChildAt(0) // Find upload button within slot
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `documentsTab - shows verified badge for uploaded documents`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.DOCUMENTS)
        }

        composeTestRule.onNodeWithTag("id_document_verified_badge")
            .assertIsDisplayed()
    }

    @Test
    fun `documentsTab - download button for verified documents`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.DOCUMENTS)
        }

        composeTestRule.onNodeWithTag("id_document_download_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MESSAGES TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `messagesTab - displays messages list`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.MESSAGES)
        }

        composeTestRule.onNodeWithTag("messages_list")
            .assertIsDisplayed()
    }

    @Test
    fun `messagesTab - shows compose button`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.MESSAGES)
        }

        composeTestRule.onNodeWithTag("compose_message_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `notificationsTab - displays notifications list`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.NOTIFICATIONS)
        }

        composeTestRule.onNodeWithTag("notifications_list")
            .assertIsDisplayed()
    }

    @Test
    fun `notificationsTab - shows clear all button`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(currentTab = MemberTabIndex.NOTIFICATIONS)
        }

        composeTestRule.onNodeWithTag("clear_all_notifications_button")
            .assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE TAB TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `profileTab - displays member information`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember, currentTab = MemberTabIndex.PROFILE)
        }

        composeTestRule.onNodeWithTag("profile_name")
            .assertIsDisplayed()
            .assertTextContains("John Doe")

        composeTestRule.onNodeWithTag("profile_email")
            .assertTextContains("john@example.com")

        composeTestRule.onNodeWithTag("profile_phone")
            .assertTextContains("0715555555")
    }

    @Test
    fun `profileTab - photo upload clickable`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember, currentTab = MemberTabIndex.PROFILE)
        }

        composeTestRule.onNodeWithTag("profile_photo")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `profileTab - shows edit profile button`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember, currentTab = MemberTabIndex.PROFILE)
        }

        composeTestRule.onNodeWithTag("edit_profile_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `profileTab - shows logout button`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember, currentTab = MemberTabIndex.PROFILE)
        }

        composeTestRule.onNodeWithTag("logout_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB NAVIGATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `tabNavigation - switching tabs updates content`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = testMember)
        }

        // Start on Overview tab
        composeTestRule.onNodeWithTag("tab_overview")
            .performClick()
        composeTestRule.onNodeWithTag("recent_activity_section")
            .assertIsDisplayed()

        // Switch to Transactions tab
        composeTestRule.onNodeWithTag("tab_transactions")
            .performClick()
        composeTestRule.onNodeWithTag("transaction_list")
            .assertIsDisplayed()

        // Switch to Loans tab
        composeTestRule.onNodeWithTag("tab_loans")
            .performClick()
        composeTestRule.onNodeWithTag("loan_eligibility_banner")
            .assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MULTI-GROUP SWITCHING TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `groupSwitcher - displays all member groups`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = multiGroupMember)
        }

        composeTestRule.onNodeWithTag("group_switcher_dropdown")
            .performClick()

        composeTestRule.onNodeWithTag("group_option_g1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_option_g2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_option_g3").assertIsDisplayed()
    }

    @Test
    fun `groupSwitcher - current group highlighted with checkmark`() {
        composeTestRule.setContent {
            // MemberDashboardScreen(member = multiGroupMember, currentGroupId = "g1")
        }

        composeTestRule.onNodeWithTag("group_switcher_dropdown")
            .performClick()

        composeTestRule.onNodeWithTag("group_option_g1_checkmark")
            .assertIsDisplayed()
    }

    @Test
    fun `groupSwitcher - switching group reloads data`() {
        var dataReloaded = false
        composeTestRule.setContent {
            // MemberDashboardScreen(onGroupSwitch = { dataReloaded = true })
        }

        composeTestRule.onNodeWithTag("group_switcher_dropdown")
            .performClick()

        composeTestRule.onNodeWithTag("group_option_g2")
            .performClick()

        assert(dataReloaded)
    }
}

