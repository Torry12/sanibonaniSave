package com.sanibonani.save

import android.os.Looper
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.group.UploadGroupConstitutionUseCase
import com.sanibonani.save.features.group.presentation.GroupRegistrationHost
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationSubmitter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The constitution upload is deliberately non-fatal - losing a created group because a PDF could
 * not be stored is worse - but the run used to report nothing but success, so an admin believed the
 * group's rules were on file when they were only in memory. A refusal must be reported as a warning
 * alongside the success, and the activation dedup check must say so when it cannot read the state it
 * is deduplicating against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupConstitutionUploadWarningTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private class RecordingHost : GroupRegistrationHost {
        private val _state = MutableStateFlow(GroupRegistrationState())
        override val state: StateFlow<GroupRegistrationState> = _state
        private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        override fun updateState(reducer: (GroupRegistrationState) -> GroupRegistrationState) {
            _state.value = reducer(_state.value)
        }

        override fun launchJob(key: String, block: suspend CoroutineScope.() -> Unit): Job =
            scope.launch(block = block)
    }

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val activateGroup = mockk<ActivateGroupUseCase>()
    private val uploadConstitution = mockk<UploadGroupConstitutionUseCase>()
    private val getGroupById = mockk<GetGroupByIdUseCase>()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var host: RecordingHost
    private lateinit var submitter: GroupRegistrationSubmitter

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk<FirebaseCrashlytics>(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns null
        coEvery { activateGroup(any(), any()) } returns Result.success(Unit)
        coEvery { getGroupById(any()) } returns Result.failure(IOException("group read refused"))

        host = RecordingHost()
        submitter = GroupRegistrationSubmitter(
            createGroup = mockk<CreateGroupUseCase>(relaxed = true),
            activateGroup = activateGroup,
            getAttributableActorId = GetAttributableActorIdUseCase(supabaseRepo),
            userProfileCache = null,
            memberContextCacheService = null,
            uploadGroupConstitution = uploadConstitution,
            getGroupById = getGroupById
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun pendingConstitution(fee: Money = Money.ZERO) {
        host.updateState {
            it.copy(createdGroupId = "g-1", platformRegistrationFee = fee, pendingConstitutionName = "rules.pdf")
        }
        submitter.selectConstitution("rules.pdf", "%PDF-1.4".toByteArray(), host)
    }

    @Test
    fun `a constitution the server refuses still leaves the group active, but is reported`() = runTest {
        coEvery { uploadConstitution(any(), any(), any()) } returns Result.failure(IOException("bucket not found"))
        pendingConstitution()

        submitter.submit(host)

        assertEquals(true, host.state.value.success)
        assertNull(host.state.value.error)
        assertNotNull(host.state.value.constitutionWarning)
    }

    @Test
    fun `a constitution that uploads on the retry clears the warning`() = runTest {
        coEvery { uploadConstitution(any(), any(), any()) } returnsMany listOf(
            Result.failure(IOException("bucket not found")),
            Result.success("https://storage/rules.pdf")
        )
        pendingConstitution()

        submitter.submit(host)
        assertNotNull(host.state.value.constitutionWarning)

        submitter.submit(host)

        assertNull(host.state.value.constitutionWarning)
        assertEquals("https://storage/rules.pdf", host.state.value.constitutionUrl)
    }

    @Test
    fun `a constitution upload that answers adds the url and no warning`() = runTest {
        coEvery { uploadConstitution(any(), any(), any()) } returns Result.success("https://storage/rules.pdf")
        pendingConstitution()

        submitter.submit(host)

        assertEquals("https://storage/rules.pdf", host.state.value.constitutionUrl)
        assertNull(host.state.value.constitutionWarning)
    }

    @Test
    fun `an unreadable registration state is logged when activation is not deduplicated`() = runTest {
        coEvery { uploadConstitution(any(), any(), any()) } returns Result.success("https://storage/rules.pdf")
        pendingConstitution(fee = Money("700.00"))

        submitter.finalizeAfterPayment("tx-9", host)

        assertEquals(true, host.state.value.success)
        coVerify(exactly = 1) { activateGroup("g-1", "tx-9") }
        assertTrue(
            "a dedup check that could not answer has to be traceable",
            AppLogger.getBreadcrumbs().any { it.contains("Registration state is unreadable") }
        )
    }

    @Test
    fun `an answered paid group is not activated twice`() = runTest {
        coEvery { getGroupById("g-1") } returns Result.success(Group(id = "g-1", registrationPaid = true))
        pendingConstitution(fee = Money("700.00"))

        submitter.finalizeAfterPayment("tx-9", host)

        coVerify(exactly = 0) { activateGroup(any(), any()) }
        assertEquals(true, host.state.value.success)
    }
}
