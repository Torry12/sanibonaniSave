package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.Group

/**
 * Centralized parsing + validation for registration form numeric fields.
 * Keeps parsing rules out of ViewModel orchestration logic.
 */
object RegisterGroupValidator {

    fun toGroupDraft(state: RegisterGroupState): Result<Group> = runCatching {
        val joiningFee = state.joiningFee.parseMoney("Please enter a valid joining fee.")
        val monthlyContribution = state.monthlyContribution.parseMoney("Please enter a valid monthly contribution.")
        val lateFee = state.lateFee.parseMoney("Please enter a valid late payment fine.")
        val maxMembers = state.maxMembers.parseInt("Please enter a valid maximum member count.")
            .also { require(it >= 1) { "Maximum members must be at least 1." } }

        val graceDays = state.lateFeeGraceDays.parseInt("Please enter valid late-fee grace days.")
            .also { require(it >= 0) { "Grace days cannot be negative." } }

        val probationMonths = state.probationMonths.parseInt("Please enter a valid probation period.")
            .also { require(it >= 0) { "Probation months cannot be negative." } }

        val paymentDueDay = state.paymentDueDay.parseInt("Please enter a valid payment due day.")
            .also { require(it in 1..31) { "Payment due day must be between 1 and 31." } }

        val goalAmount = state.goalAmount.parseMoney("Please enter a valid goal amount.")
            .also { require(it >= 0.0) { "Goal amount cannot be negative." } }

        val periodMonths = state.periodMonths.parseInt("Please enter a valid savings period.")
            .also { require(it >= 1) { "Savings period must be at least 1 month." } }

        val maxBeneficiaries = state.maxBeneficiaries.toIntOrNull()?.also {
            require(it >= 0) { "Maximum beneficiaries cannot be negative." }
        }

        val beneficiaryIncreasePct = state.beneficiaryIncreasePct.toDoubleOrNull()?.also {
            require(it >= 0.0) { "Beneficiary increase percent cannot be negative." }
        }


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
            bankName = state.bankName.trim(),
            accountNumber = state.accountNumber.trim(),
            branchCode = state.branchCode.trim(),
            maxBeneficiaries = maxBeneficiaries?.takeIf { it > 0 },
            beneficiaryIncreasePct = beneficiaryIncreasePct?.takeIf { it > 0.0 },
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
            constitutionStatus = state.constitutionStatus
        )
    }

    private fun String.parseMoney(errorMessage: String): Double {
        val parsed = trim().toDoubleOrNull() ?: throw IllegalArgumentException(errorMessage)
        require(parsed >= 0.0) { errorMessage }
        return parsed
    }

    private fun String.parseInt(errorMessage: String): Int =
        trim().toIntOrNull() ?: throw IllegalArgumentException(errorMessage)
}

