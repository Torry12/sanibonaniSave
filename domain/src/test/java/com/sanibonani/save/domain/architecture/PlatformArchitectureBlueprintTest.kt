package com.sanibonani.save.domain.architecture

import com.sanibonani.save.domain.model.GroupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformArchitectureBlueprintTest {

    @Test
    fun `catalog includes all major financial models`() {
        val blueprint = PlatformArchitectureBlueprintCatalog.current()
        assertEquals(10, blueprint.groupModels.size)
        assertEquals(FinancialGroupModel.values().toSet(), blueprint.groupModels.map { it.model }.toSet())
    }

    @Test
    fun `each model has backend, db, api and workflow coverage`() {
        val blueprint = PlatformArchitectureBlueprintCatalog.current()

        blueprint.groupModels.forEach { model ->
            assertFalse("backend functions missing for ${model.model}", model.backendFunctions.isEmpty())
            assertFalse("database tables missing for ${model.model}", model.databaseTables.isEmpty())
            assertFalse("api surface missing for ${model.model}", model.apiSurface.isEmpty())
            assertFalse("workflows missing for ${model.model}", model.workflows.isEmpty())
        }
    }

    @Test
    fun `api operation ids are unique across the platform`() {
        val operationIds = PlatformArchitectureBlueprintCatalog.current()
            .groupModels
            .flatMap { model -> model.apiSurface.map { it.operationId } }

        assertEquals(operationIds.size, operationIds.toSet().size)
    }

    @Test
    fun `existing group types map to architecture models`() {
        val mappings = GroupType.values().associateWith { it.toFinancialGroupModel() }

        assertNotNull(mappings[GroupType.ROSCA])
        assertNotNull(mappings[GroupType.BURIAL_SOCIETY])
        assertNotNull(mappings[GroupType.INVESTMENT_CLUB])
        assertNotNull(mappings[GroupType.OTHER])
        assertTrue(mappings.isNotEmpty())
    }
}

