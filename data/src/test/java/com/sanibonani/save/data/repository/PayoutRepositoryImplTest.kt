package com.sanibonani.save.data.repository

import com.sanibonani.save.data.datasource.LocalPayoutDataSource
import com.sanibonani.save.data.datasource.RemotePayoutDataSource
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.data.repository.disbursement.PayoutRepositoryImpl
import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PayoutType
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PayoutRepositoryImplTest {

    private val remoteSource: RemotePayoutDataSource = mockk()
    private val localSource: LocalPayoutDataSource = mockk(relaxed = true)
    private val auditLogRepo: AuditLogRepository = mockk(relaxed = true)
    private val pendingOpDao: PendingOperationDao = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private fun repository() = PayoutRepositoryImpl(
        remoteSource,
        localSource,
        auditLogRepo,
        json,
        pendingOpDao
    )

    private fun payout(memberId: String?) = PayoutRequest(
        id = "p1",
        groupId = "g1",
        memberId = memberId,
        amount = Money("100.00"),
        bankName = "FNB",
        accountNo = "123456789",
        branchCode = "250655",
        status = PayoutStatus.PROCESSING,
        type = PayoutType.ANNUAL,
        processedBy = "admin1",
        payoutReference = "REF-1"
    )

    @Test
    fun `completing a member payout announces the member, not the admin who completed it`() = runBlocking {
        coEvery { remoteSource.completePayoutAtomic("p1", "admin1", "REF-1") } returns Result.success(Unit)
        coEvery { remoteSource.getPayoutById("p1") } returns Result.success(payout("m1"))
        val received = CopyOnWriteArrayList<DomainEvent>()
        val collector = launch(context = Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            EventBus.events.collect { received.add(it) }
        }

        val result = repository().completePayoutAtomic("p1", "admin1", "REF-1")

        collector.cancel()
        assertTrue(result.isSuccess)
        val event = received.filterIsInstance<PaymentProcessedEvent>().single()
        assertEquals("m1", event.payment.memberId)
    }

    @Test
    fun `completing a group payout does not attribute the payment to the admin`() = runBlocking {
        coEvery { remoteSource.completePayoutAtomic("p1", "admin1", "REF-1") } returns Result.success(Unit)
        coEvery { remoteSource.getPayoutById("p1") } returns Result.success(payout(memberId = null))
        val received = CopyOnWriteArrayList<DomainEvent>()
        val collector = launch(context = Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            EventBus.events.collect { received.add(it) }
        }

        val result = repository().completePayoutAtomic("p1", "admin1", "REF-1")

        collector.cancel()
        assertTrue(result.isSuccess)
        assertTrue(received.filterIsInstance<PaymentProcessedEvent>().isEmpty())
    }

    @Test
    fun `the status audit records the status the payout actually moved from`() = runBlocking {
        val captured = slot<AuditLog>()
        coEvery { remoteSource.getPayoutById("p1") } returnsMany listOf(
            Result.success(payout("m1").copy(status = PayoutStatus.PENDING)),
            Result.success(payout("m1").copy(status = PayoutStatus.GROUP_APPROVED))
        )
        coEvery {
            remoteSource.updatePayoutStatus("p1", PayoutStatus.GROUP_APPROVED, null, "admin1", null)
        } returns Result.success(Unit)
        coEvery { auditLogRepo.logEvent(capture(captured)) } just Runs

        val result = repository().updatePayoutStatus("p1", PayoutStatus.GROUP_APPROVED, actorId = "admin1")

        assertTrue(result.isSuccess)
        val details = captured.captured.details as JsonObject
        assertEquals("PENDING", details["old_status"]?.jsonPrimitive?.content)
        assertEquals("GROUP_APPROVED", details["new_status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `complete payout rpc params use fallback nil uuid for platform admin`() {
        val params = PayoutRepositoryImpl.buildCompletePayoutRpcParams(
            payoutId = "11111111-1111-1111-1111-111111111111",
            adminId = "PLATFORM_ADMIN",
            fallbackAdminId = null,
            payoutReference = "ref-123"
        )

        assertEquals("11111111-1111-1111-1111-111111111111", params["p_payout_id"]?.jsonPrimitive?.content)
        assertEquals("ref-123", params["p_payout_reference"]?.jsonPrimitive?.content)
        assertEquals("00000000-0000-0000-0000-000000000000", params["p_admin_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `complete payout rpc params use valid fallback admin id`() {
        val params = PayoutRepositoryImpl.buildCompletePayoutRpcParams(
            payoutId = "11111111-1111-1111-1111-111111111111",
            adminId = "PLATFORM_ADMIN",
            fallbackAdminId = "22222222-2222-2222-2222-222222222222",
            payoutReference = null
        )

        assertEquals("22222222-2222-2222-2222-222222222222", params["p_admin_id"]?.jsonPrimitive?.content)
        assertFalse(params.containsKey("p_payout_reference"))
    }
}
