package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull

object RegisterGroupValidator {
    fun toGroupDraft(state: GroupRegistrationState): Result<Group> = runCatching {
        val joiningFee = state.joiningFee.parseMoney("Invalid joining fee")
        val monthlyContribution = state.monthlyContribution.parseMoney("Invalid monthly contribution")
        val lateFeeValue = if (state.lateFee.isBlank()) 0.0 else state.lateFee.toDoubleOrNull() ?: throw Exception("Invalid late fee")
        val lateFee = Money(lateFeeValue)

        val maxMembers = if (state.type == GroupType.ROSCA) state.roscaCycle else state.maxMembers.toIntOrNull() ?: 50
        val graceDays = state.lateFeeGraceDays.toIntOrNull() ?: 5
        val probationMonths = state.probationMonths.toIntOrNull() ?: 3
        val paymentDueDay = state.paymentDueDay.toIntOrNull() ?: 28
        val goalAmount = Money(state.goalAmount.toDoubleOrNull() ?: 0.0)
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
            bankName = state.bankName.trim().takeIf { it.isNotBlank() },
            accountNumber = state.accountNumber.trim().takeIf { it.isNotBlank() },
            branchCode = state.branchCode.trim().takeIf { it.isNotBlank() },
            maxBeneficiaries = state.maxBeneficiaries.toIntOrNull()?.takeIf { it > 0 },
            beneficiaryIncreasePct = state.beneficiaryIncreasePct.toDoubleOrNull()?.takeIf { it > 0.0 },
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
}
