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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A beneficiary is who the treasury pays out on a burial or emergency claim, so an unreadable ID
 * number is not a formatting nuisance - it is the record that a future claim will be settled
 * against. The group's own members are already Luhn-checked; this holds the beneficiary to the
 * same brain instead of storing whatever was typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberBeneficiaryIdValidationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val beneficiaryRepo = mockk<BeneficiaryRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MemberBeneficiaryViewModel

    // Luhn-valid per InputValidator.verifySAIdChecksum, DOB segment 800101.
    private val validId = "8001015009087"

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

    private fun capturedBeneficiary(): Beneficiary {
        val captured = slot<Beneficiary>()
        coVerify(exactly = 1) { beneficiaryRepo.addBeneficiary(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `an id number that fails the checksum is never stored`() = runTest {
        viewModel.addBeneficiary("Bheki Zulu", "8001015009086", "Brother", "1980-01-01", false)
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.addBeneficiary(any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
        assertTrue((events.single() as UIEvent.ShowError).message.contains("ID number"))
    }

    @Test
    fun `every unreadable id number is refused and reported`() = runTest {
        val unusable = listOf("", "        ", "800101", "not an id", "800101500908O")
        unusable.forEach { bad ->
            viewModel.addBeneficiary("Bheki Zulu", bad, "Brother", "1980-01-01", false)
            advanceUntilIdle()
        }

        coVerify(exactly = 0) { beneficiaryRepo.addBeneficiary(any()) }

        // Silently swallowing the tap is how a half-typed ID ends up saved by retrying until the
        // button "works"; each rejection has to say why.
        val events = viewModel.events.take(unusable.size).toList()
        assertEquals(unusable.size, events.count { it is UIEvent.ShowError })
    }

    @Test
    fun `a valid id number is stored with its separators removed`() = runTest {
        coEvery { beneficiaryRepo.addBeneficiary(any()) } returns Result.success(Beneficiary(
            memberId = "m1", fullName = "Bheki Zulu", idNumber = validId, relationship = "Brother"
        ))

        viewModel.addBeneficiary("Bheki Zulu", " $validId ", "Brother", "1980-01-01", true)
        advanceUntilIdle()

        val stored = capturedBeneficiary()
        assertEquals(validId, stored.idNumber)
        assertEquals("m1", stored.memberId)
        assertEquals("Brother", stored.relationship)
    }

    @Test
    fun `the row carries the group foreign key and the fields the dialog collected`() = runTest {
        coEvery { beneficiaryRepo.addBeneficiary(any()) } returns Result.success(Beneficiary(
            memberId = "m1", fullName = "Bheki Zulu", idNumber = validId
        ))

        viewModel.addBeneficiary("Bheki Zulu", validId, "Brother", "1980-01-01", true)
        advanceUntilIdle()

        // beneficiaries.group_id is a NOT NULL uuid with a FK to groups. Sending the model default
        // "" makes PostgREST reject the insert, so the whole roster silently stays empty.
        val stored = capturedBeneficiary()
        assertEquals("g1", stored.groupId)
        assertEquals("1980-01-01", stored.dateOfBirth)
        assertTrue(stored.isOver65)
    }

    @Test
    fun `a beneficiary is refused rather than written against no group`() = runTest {
        viewModel.setImpersonationContext(
            Member(id = "m1", groupId = "", fullName = "Alpha Member", status = MemberStatus.ACTIVE),
            null
        )

        viewModel.addBeneficiary("Bheki Zulu", validId, "Brother", "1980-01-01", false)
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.addBeneficiary(any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    @Test
    fun `a blank name is refused and says why`() = runTest {
        viewModel.addBeneficiary("   ", validId, "Brother", "1980-01-01", false)
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.addBeneficiary(any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
        assertTrue((events.single() as UIEvent.ShowError).message.contains("name"))
    }
}
