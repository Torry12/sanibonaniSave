package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.FraudRiskLevel
import com.sanibonani.save.domain.model.MemberBehaviorTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorScoringUtilsTest {

    @Test
    fun `calculateBehaviorScore handles zero history safely`() {
        val track = MemberBehaviorTrack()

        val score = BehaviorScoringUtils.calculateBehaviorScore(track)

        assertEquals(64.5, score, 0.0001)
    }

    @Test
    fun `detectUnusualPaymentPattern returns false when baseline average is zero`() {
        val contributions = listOf(
            mapOf("member_id" to "member-1", "amount" to 100.0, "created_at" to "2026-05-14T10:00:00Z"),
            mapOf("member_id" to "member-1", "amount" to 0.0, "created_at" to "2026-04-14T10:00:00Z"),
            mapOf("member_id" to "member-1", "amount" to 0.0, "created_at" to "2026-03-14T10:00:00Z"),
            mapOf("member_id" to "member-1", "amount" to 0.0, "created_at" to "2026-02-14T10:00:00Z"),
            mapOf("member_id" to "member-1", "amount" to 0.0, "created_at" to "2026-01-14T10:00:00Z")
        )

        val isUnusual = BehaviorScoringUtils.detectUnusualPaymentPattern(contributions, "member-1")

        assertFalse(isUnusual)
    }

    @Test
    fun `determineFraudRiskLevel marks critical for combined account and velocity signals`() {
        val track = MemberBehaviorTrack(
            multipleAccountsDetected = true,
            velocityCheckFailed = true
        )

        val risk = BehaviorScoringUtils.determineFraudRiskLevel(fraudScore = 10.0, track = track)

        assertEquals(FraudRiskLevel.CRITICAL, risk)
    }

    @Test
    fun `determineFraudRiskLevel transitions by score thresholds`() {
        val medium = BehaviorScoringUtils.determineFraudRiskLevel(45.0, MemberBehaviorTrack())
        val high = BehaviorScoringUtils.determineFraudRiskLevel(65.0, MemberBehaviorTrack())
        val critical = BehaviorScoringUtils.determineFraudRiskLevel(85.0, MemberBehaviorTrack())

        assertEquals(FraudRiskLevel.MEDIUM, medium)
        assertEquals(FraudRiskLevel.HIGH, high)
        assertEquals(FraudRiskLevel.CRITICAL, critical)
        assertTrue(critical != high)
    }
}

