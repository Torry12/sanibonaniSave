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
        val lateFee = state.lateFee.parseMoneyOrDefault(Money.ZERO)

        val maxMembers = if (state.type == GroupType.ROSCA) state.roscaCycle else state.maxMembers.toIntOrNull() ?: 50
        val graceDays = state.lateFeeGraceDays.toIntOrNull() ?: 5
        val probationMonths = state.probationMonths.toIntOrNull() ?: 3
        val paymentDueDay = state.paymentDueDay.toIntOrNull() ?: 28
        val goalAmount = state.goalAmount.parseMoneyOrDefault(Money.ZERO)
        val periodMonths = if (state.type == GroupType.ROSCA) state.roscaCycle else state.periodMonths.toIntOrNull() ?: 6

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
            maxBeneficiaries = state.maxBeneficiaries.toIntOrNull()?.takeIf { it > 0 },
            beneficiaryIncreasePct = state.beneficiaryIncreasePct.toDoubleOrNull()?.takeIf { it > 0.0 },
            burialClaimAmount = state.burialClaimAmount.parseMoneyOrDefault(Money("10000.00")),
            minMembersForClaims = state.minMembersForClaims.toIntOrNull() ?: 10,
            minReservePercentage = state.minReservePercentage.toDoubleOrNull() ?: 20.0,
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

    private fun String.parseMoneyOrDefault(default: Money): Money {
        if (this.isBlank()) return default
        return parseMoneyAmountOrNull()?.let { Money(it) } ?: default
    }
}
