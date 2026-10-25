package com.sanibonani.save.validation

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.features.common.validation.BeneficiaryForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeneficiaryFormTest {

    // Luhn-valid per InputValidator.verifySAIdChecksum, DOB segment 800101.
    private val validId = "8001015009087"

    private val template = Beneficiary(
        id = "b1",
        groupId = "g1",
        memberId = "m1",
        fullName = "Old Name",
        relationship = "Sibling",
        dateOfBirth = "1970-01-01",
        documentUrl = "https://example.test/id.jpg",
        documentStatus = DocumentStatus.VERIFIED
    )

    private fun valid(
        name: String = "Sipho Zulu",
        idNumber: String = validId,
        relationship: String = "Spouse",
        dateOfBirth: String = "1980-01-01",
        isOver65: Boolean = false
    ) = BeneficiaryForm.validate(template, name, idNumber, relationship, dateOfBirth, isOver65)

    @Test
    fun `valid input is trimmed and keeps the row identity and documents`() {
        val result = valid(name = "  Sipho Zulu  ", idNumber = " $validId ", relationship = " Spouse ")

        assertTrue("expected a valid record, got $result", result is BeneficiaryForm.Validation.Valid)
        val record = (result as BeneficiaryForm.Validation.Valid).record
        assertEquals("b1", record.id)
        assertEquals("g1", record.groupId)
        assertEquals("m1", record.memberId)
        assertEquals("Sipho Zulu", record.fullName)
        assertEquals(validId, record.idNumber)
        assertEquals("Spouse", record.relationship)
        assertEquals("1980-01-01", record.dateOfBirth)
        assertEquals("https://example.test/id.jpg", record.documentUrl)
        assertEquals(DocumentStatus.VERIFIED, record.documentStatus)
    }

    @Test
    fun `blank optional fields are stored as absent rather than empty strings`() {
        val result = valid(relationship = "   ", dateOfBirth = "   ", isOver65 = true)

        assertTrue("expected a valid record, got $result", result is BeneficiaryForm.Validation.Valid)
        val record = (result as BeneficiaryForm.Validation.Valid).record
        assertNull(record.relationship)
        assertNull(record.dateOfBirth)
        assertTrue(record.isOver65)
    }

    @Test
    fun `a blank name is refused with a name message`() {
        val result = valid(name = "   ")

        assertTrue("expected a rejection, got $result", result is BeneficiaryForm.Validation.Invalid)
        assertTrue((result as BeneficiaryForm.Validation.Invalid).message.contains("name"))
    }

    @Test
    fun `a blank id number is refused with an id message`() {
        val result = valid(idNumber = "   ")

        assertTrue("expected a rejection, got $result", result is BeneficiaryForm.Validation.Invalid)
        assertTrue((result as BeneficiaryForm.Validation.Invalid).message.contains("ID number"))
    }

    @Test
    fun `an id number that fails the checksum is refused`() {
        val result = valid(idNumber = "8001015009086")

        assertTrue("expected a rejection, got $result", result is BeneficiaryForm.Validation.Invalid)
        assertTrue((result as BeneficiaryForm.Validation.Invalid).message.contains("ID number"))
    }
}
