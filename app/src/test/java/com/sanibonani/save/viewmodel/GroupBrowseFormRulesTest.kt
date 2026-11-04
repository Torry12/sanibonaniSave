package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.features.group.presentation.browseGroupsNotice
import com.sanibonani.save.features.group.presentation.placeableGroups
import com.sanibonani.save.features.group.presentation.provinceRowExpanded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Three rules the discovery screen used to express inline, each with a case it got wrong: a refusal
 * recorded while cached rows stayed on screen was rendered nowhere, the map decided it had no data
 * from a set the active filters had already narrowed away, and a province header could not be
 * collapsed while a search was running.
 */
class GroupBrowseFormRulesTest {

    private val refusal = IOException("IO Error: HTTP request to rest/v1/groups failed").message

    @Test
    fun `a refusal that arrives over cached rows is still shown`() {
        assertEquals(refusal, browseGroupsNotice(refusal, hasCachedRows = true))
    }

    @Test
    fun `no notice is raised when the list answered`() {
        assertNull(browseGroupsNotice(null, hasCachedRows = true))
        assertNull(browseGroupsNotice(null, hasCachedRows = false))
    }

    @Test
    fun `an empty list refusal belongs to the empty state, not the notice`() {
        // The screen already renders the refusal as the whole body when there is nothing to list,
        // so a second banner over the empty state would report the same refusal twice.
        assertNull(browseGroupsNotice(refusal, hasCachedRows = false))
    }

    @Test
    fun `a group with coordinates is placeable`() {
        val located = Group(id = "g-1", name = "Located", type = GroupType.STOKVEL, latitude = -26.2, longitude = 27.8)

        assertEquals(listOf(located), placeableGroups(listOf(located)))
    }

    @Test
    fun `a group with no coordinates is placed at its province centre`() {
        val provincial = Group(id = "g-2", name = "Provincial", type = GroupType.STOKVEL, province = "Gauteng")

        assertEquals(listOf(provincial), placeableGroups(listOf(provincial)))
    }

    @Test
    fun `a group the map cannot place is left out`() {
        val located = Group(id = "g-1", name = "Located", type = GroupType.STOKVEL, latitude = -26.2)
        val nowhere = Group(id = "g-3", name = "Nowhere", type = GroupType.STOKVEL, province = "Bikini Bottom")

        assertEquals(listOf(located), placeableGroups(listOf(located, nowhere)))
    }

    @Test
    fun `the map set comes from the rows the filters left`() {
        val gauteng = Group(id = "g-1", name = "Gauteng Row", type = GroupType.STOKVEL, province = "Gauteng")
        val westernCape = Group(id = "g-2", name = "Cape Row", type = GroupType.STOKVEL, province = "Western Cape")

        // Narrowing to one province has to narrow the map too, otherwise the map shows rows the
        // search field just filtered out - and the empty-state gate cannot see that it did.
        assertEquals(listOf(westernCape), placeableGroups(listOf(gauteng, westernCape).filter { it.province == "Western Cape" }))
        assertEquals(emptyList<Group>(), placeableGroups(listOf(gauteng, westernCape).filter { it.province == "Free State" }))
    }

    @Test
    fun `a province starts open while searching and closed otherwise`() {
        assertEquals(true, provinceRowExpanded(stored = null, isSearching = true))
        assertEquals(false, provinceRowExpanded(stored = null, isSearching = false))
    }

    @Test
    fun `an explicit province choice wins over the search default`() {
        assertEquals(false, provinceRowExpanded(stored = false, isSearching = true))
        assertEquals(true, provinceRowExpanded(stored = true, isSearching = false))
        assertEquals(true, provinceRowExpanded(stored = true, isSearching = true))
    }
}
