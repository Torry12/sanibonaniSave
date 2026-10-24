package com.sanibonani.save.features.common.validation

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationResult

/**
 * Normalises the beneficiary dialog's raw text and holds the ID check that both the member and the
 * admin roster use. A beneficiary is the record a future burial or emergency claim is settled
 * against, so an unreadable ID is refused before anything is queued.
 */
internal object BeneficiaryForm {

    sealed interface Validation {
        data class Valid(val record: Beneficiary) : Validation
        data class Invalid(val message: String) : Validation
    }

    fun validate(
        template: Beneficiary,
        name: String,
        idNumber: String,
        relationship: String,
        dateOfBirth: String,
        isOver65: Boolean
    ): Validation {
        val cleanedName = name.trim()
        if (cleanedName.isBlank()) {
            return Validation.Invalid("Add the beneficiary's full name before saving.")
        }

        val cleanedId = idNumber.trim()
        val idError = when {
            cleanedId.isBlank() -> "Add the beneficiary's SA ID number before saving."
            InputValidator.validateSAIdNumber(cleanedId) !is ValidationResult.Valid ->
                "Add a valid 13-digit SA ID number for the beneficiary."
            else -> null
        }
        if (idError != null) return Validation.Invalid(idError)

        return Validation.Valid(
            template.copy(
                fullName = cleanedName,
                idNumber = cleanedId,
                relationship = relationship.trim().ifBlank { null },
                dateOfBirth = dateOfBirth.trim().ifBlank { null },
                isOver65 = isOver65
            )
        )
    }
}
