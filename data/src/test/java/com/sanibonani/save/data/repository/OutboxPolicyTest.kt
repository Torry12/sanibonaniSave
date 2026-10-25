package com.sanibonani.save.data.repository

import com.sanibonani.save.data.repository.system.OutboxPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class OutboxPolicyTest {

    @Test
    fun `deterministic rejections are permanent`() {
        assertTrue(OutboxPolicy.isPermanentFailure(IllegalArgumentException("No handler for operation type: ghost")))
        assertTrue(OutboxPolicy.isPermanentFailure(IllegalStateException("Outbox payload is missing 'amount'")))
        assertTrue(OutboxPolicy.isPermanentFailure(NumberFormatException("For input string: \"abc\"")))
        assertTrue(OutboxPolicy.isPermanentFailure(RuntimeException("HTTP 400 Bad Request")))
        assertTrue(OutboxPolicy.isPermanentFailure(RuntimeException("new row violates row-level security policy")))
        assertTrue(OutboxPolicy.isPermanentFailure(RuntimeException("permission denied for function process_complex_payment_v4")))
        assertTrue(OutboxPolicy.isPermanentFailure(RuntimeException("Insufficient group balance")))
        assertTrue(OutboxPolicy.isPermanentFailure(RuntimeException("column contribution_type does not exist")))
    }

    @Test
    fun `outages and server faults stay transient`() {
        assertFalse(OutboxPolicy.isPermanentFailure(UnknownHostException("api.example.org")))
        assertFalse(OutboxPolicy.isPermanentFailure(SocketTimeoutException("timeout")))
        assertFalse(OutboxPolicy.isPermanentFailure(ConnectException("Connection refused")))
        assertFalse(OutboxPolicy.isPermanentFailure(IOException("Connection reset")))
        assertFalse(OutboxPolicy.isPermanentFailure(RuntimeException("HTTP 500 Internal Server Error")))
        assertFalse(OutboxPolicy.isPermanentFailure(RuntimeException("HTTP 503 Service Unavailable")))
        assertFalse(OutboxPolicy.isPermanentFailure(RuntimeException("HTTP 429 Too Many Requests")))
        assertFalse(OutboxPolicy.isPermanentFailure(RuntimeException("something this policy has never seen")))
    }

    @Test
    fun `classification looks through the cause chain`() {
        val wrapped = IllegalStateException(
            "handler failed",
            IllegalArgumentException("Outbox payload is missing 'groupId'")
        )
        assertTrue(OutboxPolicy.isPermanentFailure(wrapped))
    }

    @Test
    fun `operations park at the ceiling`() {
        assertFalse(OutboxPolicy.isParked(OutboxPolicy.MAX_PERMANENT_ATTEMPTS - 1))
        assertTrue(OutboxPolicy.isParked(OutboxPolicy.MAX_PERMANENT_ATTEMPTS))
        assertTrue(OutboxPolicy.isParked(OutboxPolicy.MAX_PERMANENT_ATTEMPTS + 1))
    }
}
