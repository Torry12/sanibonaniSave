package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import com.sanibonani.save.domain.validation.ValidationUtils

object RegisterGroupValidator {
    fun toGroupDraft(state: GroupRegistrationState): Result<Group> = safeRunCatching {
        require(state.name.trim().length >= 3) { "Group Name must be at least 3 characters." }
        require(state.adminEmail.isNotBlank() && ValidationUtils.isValidEmail(state.adminEmail.trim())) { "A valid admin email address is required." }
        require(state.isLoggedIn || state.adminPassword.length >= 6) { "Admin Password must be at least 6 characters." }

        // Administrative contact is mandatory: it is the group's only channel for
        // fee reminders and suspension notices.
        val adminPhone = state.adminPhone.trim()
        require(adminPhone.isNotBlank() && ValidationUtils.isValidPhoneNumber(adminPhone)) {
            "A valid South African admin phone number is required."
        }

        // The admin ID is identity data that KYC and audit trails depend on, so a typed
        // value has to satisfy the same rule the rest of the platform applies.
        val adminIdNumber = state.adminIdNumber.trim()
        require(adminIdNumber.isBlank() || ValidationUtils.isValidSAIdNumber(adminIdNumber)) {
            "Admin SA ID must be 13 digits with a valid date of birth and checksum."
        }

        // Banking details are what payouts and fee debits are sent to; a partially
        // filled form would silently persist unusable values.
        val bankName = state.bankName.trim().takeIf { it.isNotBlank() }
        val accountNumber = state.accountNumber.trim().takeIf { it.isNotBlank() }
        val branchCode = state.branchCode.trim().takeIf { it.isNotBlank() }
        if (bankName != null || accountNumber != null || branchCode != null) {
            require(bankName != null) { "Please select a bank for the account details." }
            require(accountNumber != null && ValidationUtils.isValidBankAccount(accountNumber)) {
                "Account number must be 7-13 digits."
            }
            require(branchCode != null && ValidationUtils.isValidBranchCode(branchCode)) {
                "Branch code must be 6 digits."
            }
        }

        require(state.termsAccepted) { "Please accept the group terms to continue." }

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
        require(paymentDueDay in 1..28) { "Payment due day must be between 1 and 28." }
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
