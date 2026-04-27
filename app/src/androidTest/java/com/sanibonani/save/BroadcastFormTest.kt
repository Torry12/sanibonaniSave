package com.sanibonani.save

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import java.util.UUID

/**
 * UI Test for the Broadcast Message form in Group Admin Portal (Messaging Tab).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BroadcastFormTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testBroadcastMessageFunctionality() {
        runBlocking {
            // 1. Authenticate and create a group to ensure we have access to Admin Portal
            val email = "admin-${UUID.randomUUID()}@example.com"
            val password = "Password123!"
            // Use adminSignUp to bypass email confirmation and stabilize tests
            supabaseRepo.adminSignUp(email, password, mapOf("role" to "group_admin"), confirm = true).getOrThrow()
            supabaseRepo.signIn(email, password).getOrThrow()
            
            val group = Group(
                name = "Test Broadcast Group",
                type = GroupType.STOKVEL,
                province = "Gauteng",
                city = "Johannesburg",
                township = "Soweto",
                description = "Test Description",
                joiningFee = 0.0,
                adminUserId = supabaseRepo.currentUserId,
                registrationPaid = true
            )
            groupRepository.createGroup(group).getOrThrow()

            // 2. Navigate to Admin Dashboard -> Messaging Tab
            // Wait for the app to settle
            composeTestRule.waitForIdle()
            
            // Find "Messaging" tab at the bottom and click it
            // Note: Using substring or ignoreCase might be safer if text is slightly different
            composeTestRule.onNodeWithText("Messaging", ignoreCase = true).performClick()

            // 3. Check form elements
            // The screen has "📢 Broadcast Message" as a header
            composeTestRule.onNodeWithText("📢 Broadcast Message", substring = true).assertIsDisplayed()
            
            // The text field has label "Your message..."
            composeTestRule.onNodeWithText("Your message...", substring = true).assertExists()
            
            // 4. Type a message
            val testMessage = "Hello from AI Agent Test!"
            composeTestRule.onNodeWithText("Your message...", substring = true).performTextInput(testMessage)
            
            // 5. Click Broadcast button
            composeTestRule.onNodeWithText("Broadcast to All Members", ignoreCase = true).performClick()
            
            // 6. Verify success state (UI should show "Message broadcasted successfully!")
            composeTestRule.onNodeWithText("Message broadcasted successfully!", substring = true).assertIsDisplayed()
            
            // 7. Verify it appears in "Recent Broadcasts"
            composeTestRule.onNodeWithText(testMessage, substring = true).assertIsDisplayed()
        }
    }
}
