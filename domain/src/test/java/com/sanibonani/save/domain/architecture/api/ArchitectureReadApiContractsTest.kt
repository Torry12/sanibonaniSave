package com.sanibonani.save.domain.architecture.api

import com.sanibonani.save.domain.architecture.FinancialGroupModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureReadApiContractsTest {

    private val api: ArchitectureReadApi = BlueprintBackedArchitectureReadApi()

    @Test
    fun `getPlatformBlueprint returns current blueprint`() = runBlocking {
        val blueprint = api.getPlatformBlueprint().getOrThrow()
        assertEquals("2026.05.13", blueprint.version)
        assertEquals(10, blueprint.groupModels.size)
    }

    @Test
    fun `listFinancialModels returns summaries for all models`() = runBlocking {
        val summaries = api.listFinancialModels().getOrThrow()
        assertEquals(10, summaries.size)
        assertTrue(summaries.all { it.apiCount > 0 })
    }

    @Test
    fun `listApiOperations can filter by model`() = runBlocking {
        val allOps = api.listApiOperations().getOrThrow()
        val roscaOps = api.listApiOperations(FinancialGroupModel.ROSCA).getOrThrow()

        assertFalse(allOps.isEmpty())
        assertFalse(roscaOps.isEmpty())
        assertTrue(allOps.size >= roscaOps.size)
    }

    @Test
    fun `listAiAgentOpportunities can filter by model`() = runBlocking {
        val all = api.listAiAgentOpportunities().getOrThrow()
        val one = api.listAiAgentOpportunities(FinancialGroupModel.BURIAL_SOCIETY).getOrThrow()

        assertFalse(all.isEmpty())
        assertEquals(1, one.size)
    }
}

