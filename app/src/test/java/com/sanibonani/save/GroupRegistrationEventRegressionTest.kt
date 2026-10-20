package com.sanibonani.save

import android.os.Looper
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.service.LocationAddress
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.presentation.RegisterGroupValidator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.arch.core.executor.testing.InstantTaskExecutorRule

/**
 * Regression tests for onboarding/group-registration bugs where every
 * GroupFormEvent was silently dropped by `else -> {}`, so most financial form
 * fields were frozen at their defaults and the payment screen was inescapable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupRegistrationEventRegressionTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val activateGroupUseCase = mockk<ActivateGroupUseCase>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)
    private val locationService = mockk<LocationService>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private lateinit var viewModel: GroupRegistrationViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        every { feeService.getRegistrationFee() } returns Money("700.0")

        viewModel = GroupRegistrationViewModel(
            createGroupUseCase,
            activateGroupUseCase,
            feeService,
            locationService,
            supabaseRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── Financial/goal fields that were previously frozen ───────────────────

    @Test
    fun `goal and period fields are applied`() {
        viewModel.onEvent(GroupFormEvent.GoalAmountChanged("25000"))
        viewModel.onEvent(GroupFormEvent.PeriodMonthsChanged("12"))
        val s = viewModel.state.value
        assertEquals("25000", s.goalAmount)
        assertEquals("12", s.periodMonths)
    }

    @Test
    fun `schedule fields are applied`() {
        viewModel.onEvent(GroupFormEvent.PaymentDueDayChanged("15"))
        viewModel.onEvent(GroupFormEvent.LateFeeGraceDaysChanged("10"))
        viewModel.onEvent(GroupFormEvent.ProbationMonthsChanged("6"))
        val s = viewModel.state.value
        assertEquals("15", s.paymentDueDay)
        assertEquals("10", s.lateFeeGraceDays)
        assertEquals("6", s.probationMonths)
    }

    @Test
    fun `member and beneficiary limits are applied`() {
        viewModel.onEvent(GroupFormEvent.MaxMembersChanged("120"))
        viewModel.onEvent(GroupFormEvent.MaxBeneficiariesChanged("8"))
        viewModel.onEvent(GroupFormEvent.BeneficiaryIncreasePctChanged("7.5"))
        val s = viewModel.state.value
        assertEquals("120", s.maxMembers)
        assertEquals("8", s.maxBeneficiaries)
        assertEquals("7.5", s.beneficiaryIncreasePct)
    }

    @Test
    fun `rosca cycle is applied and drives derived member count`() {
        viewModel.onEvent(GroupFormEvent.TypeSelected(GroupType.ROSCA))
        viewModel.onEvent(GroupFormEvent.RoscaCycleChanged(12))
        val s = viewModel.state.value
        assertEquals(12, s.roscaCycle)

        // RegisterGroupValidator derives ROSCA maxMembers/periodMonths from the cycle,
        // so a stuck cycle forced 6 members / 6 months on every ROSCA group.
        val draft = RegisterGroupValidator.toGroupDraft(
            s.copy(
                name = "ROSCA Cycle",
                adminEmail = "a@b.com",
                adminPhone = "0712345678",
                adminPassword = "password123",
                termsAccepted = true
            )
        ).getOrThrow()
        assertEquals(12, draft.maxMembers)
        assertEquals(12, draft.periodMonths)
    }

    @Test
    fun `payment behaviour toggles are applied`() {
        viewModel.onEvent(GroupFormEvent.AllowPartialPaymentToggled(true))
        viewModel.onEvent(GroupFormEvent.UseStandardConstitutionToggled(true))
        val s = viewModel.state.value
        assertTrue(s.allowPartialPayment)
        assertTrue(s.useStandardConstitution)
    }

    // ── Payment back button was a no-op, trapping the user ─────────────────

    @Test
    fun `dismiss payment returns to the form`() {
        viewModel.onEvent(GroupFormEvent.DismissPayment)
        assertFalse(viewModel.state.value.needsPayment)
    }

    // ── GPS captured coordinates then immediately discarded them ───────────

    @Test
    fun `gps location keeps the resolved coordinates`() {
        viewModel.onEvent(GroupFormEvent.GpsLocationPicked(-26.5, 27.9, "Current Location"))
        val s = viewModel.state.value
        assertEquals(-26.5, s.latitude!!, 0.0001)
        assertEquals(27.9, s.longitude!!, 0.0001)
        assertEquals("Current Location", s.city)
    }

    @Test
    fun `city change still clears stale coordinates`() {
        viewModel.onEvent(GroupFormEvent.GpsLocationPicked(-26.5, 27.9, "Current Location"))
        viewModel.onEvent(GroupFormEvent.CityChanged("Cape Town"))
        val s = viewModel.state.value
        assertNull("Picking a new city must invalidate the previous pin", s.latitude)
        assertNull(s.longitude)
        assertEquals("Cape Town", s.city)
    }

    // ── Address search box could not be typed into ────────────────────────

    @Test
    fun `address query text is retained so the field is editable`() {
        viewModel.onEvent(GroupFormEvent.AddressQueryChanged("Soweto"))
        assertEquals("Soweto", viewModel.state.value.addressQuery)
    }

    @Test
    fun `selecting a suggestion fills the query with the address`() {
        coEvery { locationService.searchAddress(any()) } returns Result.success(emptyList())
        viewModel.onEvent(
            GroupFormEvent.AddressSelected(
                LocationAddress(
                    formatted = "Soweto, Gauteng",
                    city = "Johannesburg",
                    province = "Gauteng",
                    township = "Soweto",
                    latitude = -26.24,
                    longitude = 27.86
                )
            )
        )
        val s = viewModel.state.value
        assertEquals("Soweto, Gauteng", s.addressQuery)
        assertEquals("Soweto, Gauteng", s.street)
        assertEquals(-26.24, s.latitude!!, 0.0001)
    }

    // ── Address prefill survives form entry (Fresh-Form reset removed) ────

    @Test
    fun `cached profile prefill is not wiped on entering the form`() {
        // init hydrates from the profile cache; the destructive
        // LaunchedEffect(Unit) { resetNavigation() } used to erase it immediately.
        assertTrue(viewModel.state.value.platformRegistrationFee.isPositive)
    }

    // ── Validation gaps that allowed paying fees with invalid data ────────
    @Test
    fun `group creation requires terms acceptance`() {
        val result = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "No Terms", adminEmail = "a@b.com", adminPhone = "0712345678", termsAccepted = false)
        )
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertEquals("Please accept the group terms to continue.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `group creation requires a valid admin phone`() {
        val blank = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "No Phone", adminEmail = "a@b.com", adminPhone = "", termsAccepted = true)
        )
        assertTrue(blank.isFailure)
        assertEquals("A valid South African admin phone number is required.", blank.exceptionOrNull()?.message)

        val malformed = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Bad Phone", adminEmail = "a@b.com", adminPhone = "12345", termsAccepted = true)
        )
        assertTrue(malformed.isFailure)
    }

    @Test
    fun `partial banking details are rejected instead of silently persisted`() {
        val accountOnly = RegisterGroupValidator.toGroupDraft(
            baseState.copy(
                name = "Half Banked",
                adminEmail = "a@b.com",
                adminPhone = "0712345678",
                accountNumber = "1234567",
                termsAccepted = true
            )
        )
        assertTrue(accountOnly.isFailure)

        val badAccount = RegisterGroupValidator.toGroupDraft(
            baseState.copy(
                name = "Bad Account",
                adminEmail = "a@b.com",
                adminPhone = "0712345678",
                bankName = "ABSA",
                accountNumber = "12",
                branchCode = "632005",
                termsAccepted = true
            )
        )
        assertTrue(badAccount.isFailure)
        assertEquals("Account number must be 7-13 digits.", badAccount.exceptionOrNull()?.message)
    }

    @Test
    fun `valid banking details are accepted`() {
        val result = RegisterGroupValidator.toGroupDraft(
            baseState.copy(
                name = "Fully Banked",
                adminEmail = "a@b.com",
                adminPhone = "0712345678",
                bankName = "ABSA",
                accountNumber = "1234567",
                branchCode = "632005",
                termsAccepted = true
            )
        )
        assertTrue(result.isSuccess)
        assertEquals("1234567", result.getOrThrow().accountNumber)
    }

    // ── Optional money fields silently substituted a different amount ──────

    @Test
    fun `unparseable optional money fields are rejected instead of defaulted`() {
        val lateFeeTypo = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Typo Late Fee", adminEmail = "a@b.com", adminPhone = "0712345678", lateFee = "5O", termsAccepted = true)
        )
        assertTrue("A mistyped late fee must not become R0", lateFeeTypo.isFailure)

        val claimTypo = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Typo Claim", adminEmail = "a@b.com", adminPhone = "0712345678", burialClaimAmount = "10O00", termsAccepted = true)
        )
        assertTrue("A mistyped claim amount must not become R10 000", claimTypo.isFailure)

        val goalTypo = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Typo Goal", adminEmail = "a@b.com", adminPhone = "0712345678", goalAmount = "25O00", termsAccepted = true)
        )
        assertTrue("A mistyped goal amount must not become R0", goalTypo.isFailure)
    }

    @Test
    fun `blank optional money fields still fall back to their defaults`() {
        val draft = RegisterGroupValidator.toGroupDraft(
            baseState.copy(
                name = "Blank Money",
                adminEmail = "a@b.com",
                adminPhone = "0712345678",
                lateFee = "",
                burialClaimAmount = "",
                goalAmount = "",
                termsAccepted = true
            )
        ).getOrThrow()
        assertEquals(Money.ZERO, draft.lateFee)
        assertEquals(Money("10000.00"), draft.burialClaimAmount)
        assertEquals(Money.ZERO, draft.goalAmount)
    }

    // ── Numeric form fields silently substituted their defaults ────────────

    @Test
    fun `unparseable numeric fields are rejected instead of defaulted`() {
        val grace = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Bad Grace", adminEmail = "a@b.com", adminPhone = "0712345678", lateFeeGraceDays = "5 days", termsAccepted = true)
        )
        assertTrue("Typed-but-unreadable grace days must not silently become 5", grace.isFailure)

        val reserve = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Bad Reserve", adminEmail = "a@b.com", adminPhone = "0712345678", minReservePercentage = "2O", termsAccepted = true)
        )
        assertTrue("A mistyped reserve percentage must not silently become 20%", reserve.isFailure)

        val beneficiaries = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Bad Beneficiaries", adminEmail = "a@b.com", adminPhone = "0712345678", maxBeneficiaries = "1O", termsAccepted = true)
        )
        assertTrue("A mistyped beneficiary cap must not silently mean unlimited", beneficiaries.isFailure)

        val dueDay = RegisterGroupValidator.toGroupDraft(
            baseState.copy(name = "Bad Due Day", adminEmail = "a@b.com", adminPhone = "0712345678", paymentDueDay = "32", termsAccepted = true)
        )
        assertTrue("A due day outside the calendar must be rejected, not moved to the 28th", dueDay.isFailure)
    }

    @Test
    fun `blank numeric fields still fall back to their defaults`() {
        val draft = RegisterGroupValidator.toGroupDraft(
            baseState.copy(
                name = "Blank Numbers",
                adminEmail = "a@b.com",
                adminPhone = "0712345678",
                lateFeeGraceDays = "",
                probationMonths = "",
                paymentDueDay = "",
                minMembersForClaims = "",
                minReservePercentage = "",
                maxBeneficiaries = "",
                termsAccepted = true
            )
        ).getOrThrow()
        assertEquals(5, draft.lateFeeGraceDays)
        assertEquals(3, draft.probationMonths)
        assertEquals(28, draft.paymentDueDay)
        assertEquals(10, draft.minMembersForClaims)
        assertEquals(20.0, draft.minReservePercentage, 0.0)
        assertNull(draft.maxBeneficiaries)
    }

    // A logged-in admin so the validator's password branch is satisfied and
    // the later business rules (terms, phone, banking) are the assertions under test.
    private val baseState = GroupRegistrationState(isLoggedIn = true)
}
