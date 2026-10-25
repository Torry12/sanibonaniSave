package com.sanibonani.save.domain.usecase.actuarial

import com.sanibonani.save.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class ActuarialMathTest {

    @Test
    fun `risk level maps composite scores to published bands`() {
        assertEquals(RiskLevel.LOW, ActuarialMath.riskLevelFor(100))
        assertEquals(RiskLevel.LOW, ActuarialMath.riskLevelFor(80))
        assertEquals(RiskLevel.MODERATE, ActuarialMath.riskLevelFor(79))
        assertEquals(RiskLevel.MODERATE, ActuarialMath.riskLevelFor(55))
        assertEquals(RiskLevel.HIGH, ActuarialMath.riskLevelFor(54))
        assertEquals(RiskLevel.HIGH, ActuarialMath.riskLevelFor(30))
        assertEquals(RiskLevel.CRITICAL, ActuarialMath.riskLevelFor(29))
        assertEquals(RiskLevel.CRITICAL, ActuarialMath.riskLevelFor(0))
    }
}
