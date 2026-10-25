package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Pins the half of beneficiary management that used to be an unfinished comment: editing and
 * removing a record. Both paths hit the same ID brain as [MemberBeneficiaryIdValidationTest] and
 * must tell the member what happened instead of closing silently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberBeneficiaryManagementTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val beneficiaryRepo = mockk<BeneficiaryRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MemberBeneficiaryViewModel

    // Luhn-valid per InputValidator.verifySAIdChecksum, DOB segment 800101.
    private val validId = "8001015009087"

    private val stored = Beneficiary(
        id = "b1",
        groupId = "g1",
        memberId = "m1",
        fullName = "Bheki Zulu",
        idNumber = validId,
        relationship = "Brother",
        dateOfBirth = "1980-01-01",
        isOver65 = false
    )

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        viewModel = MemberBeneficiaryViewModel(
            beneficiaryRepo,
            claimRepo,
            fileStorageService,
            ValidateBurialClaimEligibilityUseCase()
        )

        viewModel.setImpersonationContext(
            Member(id = "m1", groupId = "g1", fullName = "Alpha Member", status = MemberStatus.ACTIVE),
            null
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `an edit keeps the row identity and stores the trimmed fields`() = runTest {
        coEvery { beneficiaryRepo.updateBeneficiary(any()) } returns Result.success(stored)
        val captured = slot<Beneficiary>()

        viewModel.updateBeneficiary(stored, "  Sipho Zulu  ", " $validId ", " Spouse ", "", true)
        advanceUntilIdle()

        coVerify(exactly = 1) { beneficiaryRepo.updateBeneficiary(capture(captured)) }
        val updated = captured.captured
        assertEquals("b1", updated.id)
        assertEquals("g1", updated.groupId)
        assertEquals("m1", updated.memberId)
        assertEquals("Sipho Zulu", updated.fullName)
        assertEquals(validId, updated.idNumber)
        assertEquals("Spouse", updated.relationship)
        assertNull(updated.dateOfBirth)
        assertTrue(updated.isOver65)

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowMessage)
    }

    @Test
    fun `an edit with an unreadable id is refused and never written`() = runTest {
        viewModel.updateBeneficiary(stored, "Sipho Zulu", "8001015009086", "Brother", "1980-01-01", false)
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.updateBeneficiary(any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
        assertTrue((events.single() as UIEvent.ShowError).message.contains("ID number"))
    }

    @Test
    fun `a removal deletes the row by its own keys`() = runTest {
        coEvery { beneficiaryRepo.deleteBeneficiary(any(), any(), any()) } returns Result.success(Unit)

        viewModel.deleteBeneficiary(stored)
        advanceUntilIdle()

        coVerify(exactly = 1) { beneficiaryRepo.deleteBeneficiary("g1", "m1", "b1") }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowMessage)
    }

    @Test
    fun `a row that never reached the server is not deleted`() = runTest {
        val unsaved = Beneficiary(groupId = "g1", memberId = "m1", fullName = "New Dependent")

        viewModel.deleteBeneficiary(unsaved)
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.deleteBeneficiary(any(), any(), any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    @Test
    fun `a failed removal is reported instead of swallowed`() = runTest {
        coEvery { beneficiaryRepo.deleteBeneficiary(any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))

        viewModel.deleteBeneficiary(stored)
        advanceUntilIdle()

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }
}
