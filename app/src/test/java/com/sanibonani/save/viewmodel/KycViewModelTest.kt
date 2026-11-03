package com.sanibonani.save.viewmodel

import android.net.Uri
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.service.KycService
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.kyc.presentation.KycStep
import com.sanibonani.save.features.kyc.presentation.KycViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * KYC is the gate that decides whether a member can transact, so the wizard may only say the
 * submission was accepted when the service actually accepted it. A run that was refused has to
 * look like a refusal: a screen that simply stops moving is read as still processing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KycViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val kycService = mockk<KycService>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: KycViewModel

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)
        viewModel = KycViewModel(kycService)
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun readableUri(path: String?): Uri = mockk {
        every { this@mockk.path } returns path
    }

    private fun captureIdFront(path: String = "/sdcard/DCIM/id-front.jpg") {
        viewModel.onIdCaptured(readableUri(path))
    }

    @Test
    fun `a refused kyc run is reported instead of leaving the wizard waiting`() = runTest {
        captureIdFront()
        coEvery { kycService.processKycAi(any(), any(), any()) } returns
            Result.failure(IOException("upload refused"))

        viewModel.onSelfieCaptured(readableUri("/sdcard/DCIM/selfie.jpg"), "member-1")
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
        assertEquals(
            "a submission the service refused has not verified anything",
            KycStep.SELFIE,
            viewModel.uiState.value.step
        )
    }

    @Test
    fun `an accepted kyc run advances to the success step`() = runTest {
        captureIdFront()
        coEvery { kycService.processKycAi("member-1", any(), any()) } returns Result.success(Unit)

        viewModel.onSelfieCaptured(readableUri("/sdcard/DCIM/selfie.jpg"), "member-1")
        advanceUntilIdle()

        assertEquals(KycStep.SUCCESS, viewModel.uiState.value.step)
    }

    @Test
    fun `the selfie file handed to the service is the one the user just took`() = runTest {
        captureIdFront("/sdcard/DCIM/id-front.jpg")
        val idFile = slot<File>()
        val selfieFile = slot<File>()
        coEvery { kycService.processKycAi("member-1", capture(idFile), capture(selfieFile)) } returns
            Result.success(Unit)

        viewModel.onSelfieCaptured(readableUri("/sdcard/DCIM/selfie.jpg"), "member-1")
        advanceUntilIdle()

        assertEquals("/sdcard/DCIM/id-front.jpg", idFile.captured.path)
        assertEquals("/sdcard/DCIM/selfie.jpg", selfieFile.captured.path)
    }

    // The two `?: return` guards are the only thing standing between a photo the app cannot read
    // and a submission, so they have to say something: with nothing on the form the user is left
    // on the selfie step with no way to know why nothing happened.
    @Test
    fun `a selfie the app cannot read is reported instead of dropping the submission`() = runTest {
        captureIdFront()

        viewModel.onSelfieCaptured(readableUri(null), "member-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { kycService.processKycAi(any(), any(), any()) }
        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
        assertEquals(KycStep.SELFIE, viewModel.uiState.value.step)
    }

    @Test
    fun `a missing id photo is reported before the selfie is ever sent`() = runTest {
        viewModel.onSelfieCaptured(readableUri("/sdcard/DCIM/selfie.jpg"), "member-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { kycService.processKycAi(any(), any(), any()) }
        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }

    // The shutter button swallows the CameraX failure today, so a broken camera looks like a
    // button that does nothing. The step is the only proof of progress here, so it must not move
    // and the user must be told.
    @Test
    fun `a photo the camera refused to save is reported and leaves the step where it was`() = runTest {
        viewModel.onCaptureFailed(IOException("capture aborted"))
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
        assertEquals(KycStep.ID_FRONT, viewModel.uiState.value.step)
        coVerify(exactly = 0) { kycService.processKycAi(any(), any(), any()) }
    }

    @Test
    fun `a failed capture after the id photo does not lose the captured id`() = runTest {
        captureIdFront("/sdcard/DCIM/id-front.jpg")

        viewModel.onCaptureFailed(IOException("capture aborted"))
        advanceUntilIdle()

        assertEquals("/sdcard/DCIM/id-front.jpg", viewModel.uiState.value.idUri?.path)
        assertEquals(KycStep.SELFIE, viewModel.uiState.value.step)
    }
}
