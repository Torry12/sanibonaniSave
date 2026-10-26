package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import com.sanibonani.save.domain.validation.ValidationUtils

object RegisterGroupValidator {

    private const val MSG_NAME = "Group Name must be at least 3 characters."
    private const val MSG_EMAIL = "A valid admin email address is required."
    private const val MSG_PASSWORD = "Admin Password must be at least 6 characters."
    private const val MSG_PHONE = "A valid South African admin phone number is required."
    private const val MSG_ADMIN_ID = "Admin SA ID must be 13 digits with a valid date of birth and checksum."
    private const val MSG_BANK = "Please select a bank for the account details."
    private const val MSG_ACCOUNT = "Account number must be 7-13 digits."
    private const val MSG_BRANCH = "Branch code must be 6 digits."
    private const val MSG_TERMS = "Please accept the group terms to continue."
    private const val MSG_CITY = "Please enter the city or town where the group is based."
    private const val MSG_PROVINCE = "Please select the province where the group is based."
    private const val MSG_DUE_DAY = "Payment due day must be between 1 and 28."

    fun toGroupDraft(state: GroupRegistrationState): Result<Group> = safeRunCatching {
        require(state.name.trim().length >= 3) { MSG_NAME }
        require(state.adminEmail.isNotBlank() && ValidationUtils.isValidEmail(state.adminEmail.trim())) { MSG_EMAIL }
        require(state.isLoggedIn || state.adminPassword.length >= 6) { MSG_PASSWORD }

        // Administrative contact is mandatory: it is the group's only channel for
        // fee reminders and suspension notices.
        val adminPhone = state.adminPhone.trim()
        require(adminPhone.isNotBlank() && ValidationUtils.isValidPhoneNumber(adminPhone)) { MSG_PHONE }

        // The admin ID is identity data that KYC and audit trails depend on, so a typed
        // value has to satisfy the same rule the rest of the platform applies.
        val adminIdNumber = state.adminIdNumber.trim()
        require(adminIdNumber.isBlank() || ValidationUtils.isValidSAIdNumber(adminIdNumber)) { MSG_ADMIN_ID }

        // Banking details are what payouts and fee debits are sent to; a partially
        // filled form would silently persist unusable values.
        val bankName = state.bankName.trim().takeIf { it.isNotBlank() }
        val accountNumber = state.accountNumber.trim().takeIf { it.isNotBlank() }
        val branchCode = state.branchCode.trim().takeIf { it.isNotBlank() }
        if (bankName != null || accountNumber != null || branchCode != null) {
            require(bankName != null) { MSG_BANK }
            require(accountNumber != null && ValidationUtils.isValidBankAccount(accountNumber)) { MSG_ACCOUNT }
            require(branchCode != null && ValidationUtils.isValidBranchCode(branchCode)) { MSG_BRANCH }
        }

        require(state.termsAccepted) { MSG_TERMS }

        val joiningFee = state.joiningFee.parseMoney("Invalid joining fee")
        val monthlyContribution = state.monthlyContribution.parseMoney("Invalid monthly contribution")
        val lateFee = state.lateFee.parseMoneyOrDefault(Money.ZERO, "Late fee")

        val maxMembers = if (state.type == GroupType.ROSCA) state.roscaCycle
            else state.maxMembers.parseIntOrDefault(50, "Maximum members")
        val graceDays = state.lateFeeGraceDays.parseIntOrDefault(5, "Late fee grace days")
        val probationMonths = state.probationMonths.parseIntOrDefault(3, "Probation months")
        val paymentDueDay = state.paymentDueDay.parseIntOrDefault(28, "Payment due day")
        // Same rule the group-settings path enforces: a due day above 28 never arrives in
        // February, so contributions would silently stop being due for one month a year.
        require(paymentDueDay in 1..28) { MSG_DUE_DAY }
        val goalAmount = state.goalAmount.parseMoneyOrDefault(Money.ZERO, "Goal amount")
        val periodMonths = if (state.type == GroupType.ROSCA) state.roscaCycle
            else state.periodMonths.parseIntOrDefault(6, "Period in months")

        Group(
            name = state.name.trim(),
            type = state.type,
            rotationMethod = state.rotationMethod,
            province = state.province.trim(),
            city = state.city.trim(),
            township = state.township.trim(),
            description = state.description.trim(),
            logoEmoji = state.logoEmoji,
            joiningFee = joiningFee,
            monthlyContribution = monthlyContribution,
            lateFee = lateFee,
            maxMembers = maxMembers,
            bankName = bankName,
            accountNumber = accountNumber,
            branchCode = branchCode,
            maxBeneficiaries = state.maxBeneficiaries.parseOptionalInt("Maximum beneficiaries")?.takeIf { it > 0 },
            beneficiaryIncreasePct = state.beneficiaryIncreasePct.parseOptionalDouble("Beneficiary increase percentage")?.takeIf { it > 0.0 },
            burialClaimAmount = state.burialClaimAmount.parseMoneyOrDefault(Money("10000.00"), "Burial claim amount"),
            minMembersForClaims = state.minMembersForClaims.parseIntOrDefault(10, "Minimum members for claims"),
            minReservePercentage = state.minReservePercentage.parseDoubleOrDefault(20.0, "Minimum reserve percentage").also {
                require(it in 0.0..100.0) { "Minimum reserve percentage must be between 0 and 100." }
            },
            latitude = state.latitude,
            longitude = state.longitude,
            geohash = state.geohash,
            lateFeeGraceDays = graceDays,
            probationMonths = probationMonths,
            paymentDueDay = paymentDueDay,
            allowPartialPayment = state.allowPartialPayment,
            goalAmount = goalAmount,
            periodMonths = periodMonths,
            constitutionUrl = state.constitutionUrl,
            constitutionStatus = state.constitutionStatus,
            isPublic = true
        )
    }

    /**
     * Inline validation for the step the user is trying to leave. Keys match the
     * `fieldErrors` lookups in the step composables, so every failure renders under its field.
     */
    fun validateStep(step: Int, state: GroupRegistrationState): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        when (step) {
            1 -> {
                if (state.name.trim().length < 3) errors["name"] = MSG_NAME
                validateAdminEmail(state, errors)
                // The admin password and ID fields live on the final step; demanding them here
                // would block a logged-out admin on a step that cannot satisfy the error.
                validateAdminContact(state, errors)
            }
            2 -> {
                if (state.city.isBlank()) errors["city"] = MSG_CITY
                if (state.province.isBlank()) errors["province"] = MSG_PROVINCE
            }
            3 -> validateCommitments(state, errors)
            4 -> validateBanking(state, errors)
            6 -> {
                validateAdminEmail(state, errors)
                validateAdminPassword(state, errors)
                validateAdminContact(state, errors)
                if (!state.termsAccepted) errors["termsAccepted"] = MSG_TERMS
            }
        }
        return errors
    }

    fun validateAll(state: GroupRegistrationState): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        for (step in 1..state.totalSteps) errors.putAll(validateStep(step, state))
        return errors
    }

    fun firstInvalidStep(state: GroupRegistrationState): Int? =
        (1..state.totalSteps).firstOrNull { validateStep(it, state).isNotEmpty() }

    private fun validateAdminEmail(state: GroupRegistrationState, errors: MutableMap<String, String>) {
        if (state.adminEmail.isBlank() || !ValidationUtils.isValidEmail(state.adminEmail.trim())) {
            errors["adminEmail"] = MSG_EMAIL
        }
    }

    private fun validateAdminPassword(state: GroupRegistrationState, errors: MutableMap<String, String>) {
        if (!state.isLoggedIn && state.adminPassword.length < 6) {
            errors["adminPassword"] = MSG_PASSWORD
        }
    }

    private fun validateAdminContact(state: GroupRegistrationState, errors: MutableMap<String, String>) {
        val adminPhone = state.adminPhone.trim()
        if (adminPhone.isBlank() || !ValidationUtils.isValidPhoneNumber(adminPhone)) {
            errors["adminPhone"] = MSG_PHONE
        }
        val adminIdNumber = state.adminIdNumber.trim()
        if (adminIdNumber.isNotBlank() && !ValidationUtils.isValidSAIdNumber(adminIdNumber)) {
            errors["adminIdNumber"] = MSG_ADMIN_ID
        }
    }

    private fun validateCommitments(state: GroupRegistrationState, errors: MutableMap<String, String>) {
        if (state.joiningFee.parseMoneyAmountOrNull() == null) {
            errors["joiningFee"] = "Invalid joining fee"
        }
        if (state.monthlyContribution.parseMoneyAmountOrNull() == null) {
            errors["monthlyContribution"] = "Invalid monthly contribution"
        }
        if (state.lateFee.isNotBlank() && state.lateFee.parseMoneyAmountOrNull() == null) {
            errors["lateFee"] = "Late fee must be a number, for example 150 or 150.50."
        }
        if (state.type != GroupType.ROSCA &&
            state.maxMembers.isNotBlank() &&
            state.maxMembers.toIntOrNull() == null
        ) {
            errors["maxMembers"] = "Maximum members must be a whole number."
        }
        if (state.lateFeeGraceDays.isNotBlank() && state.lateFeeGraceDays.toIntOrNull() == null) {
            errors["lateFeeGraceDays"] = "Late fee grace days must be a whole number."
        }
        if (state.probationMonths.isNotBlank() && state.probationMonths.toIntOrNull() == null) {
            errors["probationMonths"] = "Probation months must be a whole number."
        }
        if (state.paymentDueDay.isNotBlank() && state.paymentDueDay.toIntOrNull()?.let { it in 1..28 } != true) {
            errors["paymentDueDay"] = MSG_DUE_DAY
        }
        if (state.type == GroupType.STOKVEL || state.type == GroupType.INVESTMENT_CLUB) {
            if (state.goalAmount.isNotBlank() && state.goalAmount.parseMoneyAmountOrNull() == null) {
                errors["goalAmount"] = "Goal amount must be a number, for example 150 or 150.50."
            }
            if (state.periodMonths.isNotBlank() && state.periodMonths.toIntOrNull() == null) {
                errors["periodMonths"] = "Period in months must be a whole number."
            }
        }
        if (state.type == GroupType.BURIAL_SOCIETY) {
            if (state.maxBeneficiaries.isNotBlank() && state.maxBeneficiaries.toIntOrNull() == null) {
                errors["maxBeneficiaries"] = "Maximum beneficiaries must be a whole number."
            }
            if (state.beneficiaryIncreasePct.isNotBlank() && state.beneficiaryIncreasePct.toDoubleOrNull() == null) {
                errors["beneficiaryIncreasePct"] = "Beneficiary increase percentage must be a number."
            }
        }
        if (!state.termsAccepted) {
            errors["termsAccepted"] = MSG_TERMS
        }
    }

    private fun validateBanking(state: GroupRegistrationState, errors: MutableMap<String, String>) {
        val bankName = state.bankName.trim().takeIf { it.isNotBlank() }
        val accountNumber = state.accountNumber.trim().takeIf { it.isNotBlank() }
        val branchCode = state.branchCode.trim().takeIf { it.isNotBlank() }
        if (bankName == null && accountNumber == null && branchCode == null) return

        if (bankName == null) errors["bankName"] = MSG_BANK
        if (accountNumber == null || !ValidationUtils.isValidBankAccount(accountNumber)) {
            errors["accountNumber"] = MSG_ACCOUNT
        }
        if (branchCode == null || !ValidationUtils.isValidBranchCode(branchCode)) {
            errors["branchCode"] = MSG_BRANCH
        }
    }

    private fun String.parseMoney(msg: String): Money = Money(parseMoneyAmountOrNull() ?: throw Exception(msg))

    /**
     * Blank means "this group does not use that field", so the default applies. A value that was
     * typed but cannot be parsed is a mistake, and quietly persisting a different amount would
     * misstate a fee or a claim ceiling.
     */
    private fun String.parseMoneyOrDefault(default: Money, field: String = "This amount"): Money {
        if (isBlank()) return default
        return Money(parseMoneyAmountOrNull() ?: throw Exception("$field must be a number, for example 150 or 150.50."))
    }

    /**
     * Same rule as the money fields above: blank means the group accepts the documented default,
     * while a typed value that cannot be read is a mistake the admin has to correct. Silently
     * falling back would change when contributions are due or how much of the pot is held back.
     */
    private fun String.parseIntOrDefault(default: Int, field: String): Int {
        if (isBlank()) return default
        return toIntOrNull() ?: throw Exception("$field must be a whole number.")
    }

    private fun String.parseDoubleOrDefault(default: Double, field: String): Double {
        if (isBlank()) return default
        return toDoubleOrNull() ?: throw Exception("$field must be a number.")
    }

    /** Blank or zero means "no cap", but a mistyped cap must not be read as unlimited. */
    private fun String.parseOptionalInt(field: String): Int? {
        if (isBlank()) return null
        return toIntOrNull() ?: throw Exception("$field must be a whole number.")
    }

    private fun String.parseOptionalDouble(field: String): Double? {
        if (isBlank()) return null
        return toDoubleOrNull() ?: throw Exception("$field must be a number.")
    }
}
