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
import com.sanibonani.save.domain.usecase.member.AddBeneficiaryUseCase
import com.sanibonani.save.domain.usecase.member.AttachBeneficiaryDocumentUseCase
import com.sanibonani.save.domain.usecase.member.RemoveBeneficiaryUseCase
import com.sanibonani.save.domain.usecase.member.SubmitBurialClaimUseCase
import com.sanibonani.save.domain.usecase.member.UpdateBeneficiaryUseCase
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
import kotlinx.coroutines.flow.flowOf
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
 * Uploading a beneficiary's ID is two steps: the file reaches storage, then its URL is written onto
 * the beneficiary row. The second step used to be fire-and-forget, so a failed write still showed
 * "uploaded successfully" and the claim evidence was never linked. Each outcome must be reported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberBeneficiaryDocumentUploadTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val beneficiaryRepo = mockk<BeneficiaryRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MemberBeneficiaryViewModel

    private val uploadedUrl = "https://storage.test/beneficiary/b1.pdf"

    private val stored = Beneficiary(
        id = "b1",
        groupId = "g1",
        memberId = "m1",
        fullName = "Bheki Zulu",
        idNumber = "8001015009087"
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
            addBeneficiaryUseCase = AddBeneficiaryUseCase(beneficiaryRepo),
            updateBeneficiaryUseCase = UpdateBeneficiaryUseCase(beneficiaryRepo),
            removeBeneficiary = RemoveBeneficiaryUseCase(beneficiaryRepo),
            attachBeneficiaryDocument = AttachBeneficiaryDocumentUseCase(beneficiaryRepo, fileStorageService),
            submitBurialClaim = SubmitBurialClaimUseCase(claimRepo),
            fileStorageService = fileStorageService,
            validateClaimEligibility = ValidateBurialClaimEligibilityUseCase()
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

    private fun stubUpload() {
        coEvery {
            fileStorageService.uploadBeneficiaryDocument("m1", "b1", any(), "pdf")
        } returns Result.success(uploadedUrl)
    }

    @Test
    fun `a link that fails to save is reported instead of a success`() = runTest {
        stubUpload()
        every { beneficiaryRepo.observeBeneficiaries("m1") } returns flowOf(Result.success(listOf(stored)))
        coEvery { beneficiaryRepo.updateBeneficiary(any()) } returns
            Result.failure(IllegalStateException("offline"))

        viewModel.uploadBeneficiaryDocument("b1", byteArrayOf(1), "id.pdf")
        advanceUntilIdle()

        coVerify(exactly = 1) { beneficiaryRepo.updateBeneficiary(any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    @Test
    fun `a beneficiary that cannot be found is reported instead of a success`() = runTest {
        stubUpload()
        every { beneficiaryRepo.observeBeneficiaries("m1") } returns flowOf(Result.success(emptyList()))

        viewModel.uploadBeneficiaryDocument("b1", byteArrayOf(1), "id.pdf")
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.updateBeneficiary(any()) }

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    @Test
    fun `a stored document url is written onto the beneficiary row`() = runTest {
        stubUpload()
        every { beneficiaryRepo.observeBeneficiaries("m1") } returns flowOf(Result.success(listOf(stored)))
        val captured = slot<Beneficiary>()
        coEvery { beneficiaryRepo.updateBeneficiary(capture(captured)) } returns
            Result.success(stored.copy(documentUrl = uploadedUrl))

        viewModel.uploadBeneficiaryDocument("b1", byteArrayOf(1), "id.pdf")
        advanceUntilIdle()

        assertEquals(uploadedUrl, captured.captured.documentUrl)
        assertEquals("b1", captured.captured.id)

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowMessage)
    }
}
