package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationResult

internal enum class BankDetailsField {
    BANK_NAME,
    ACCOUNT_NUMBER,
    BRANCH_CODE
}

internal data class ValidatedBankDetails(
    val bankName: String,
    val accountNo: String,
    val branchCode: String
)

internal sealed interface BankDetailsResult {
    data class Valid(val details: ValidatedBankDetails) : BankDetailsResult
    data class Invalid(val field: BankDetailsField, val reason: String) : BankDetailsResult
}

/**
 * The single rule for whether money can be addressed to an account. Every disbursement entry
 * point spends from the same treasury, so they must agree on the shape of a usable bank detail.
 * The failed field is typed so each caller can keep its own user-facing wording.
 */
internal fun validateBankDetails(
    bankName: String?,
    accountNo: String?,
    branchCode: String?
): BankDetailsResult {
    val trimmedBank = bankName?.trim().orEmpty()
    if (trimmedBank.isEmpty()) {
        return BankDetailsResult.Invalid(BankDetailsField.BANK_NAME, "Bank name is required.")
    }
    val trimmedAccount = accountNo?.trim().orEmpty()
    val accountCheck = InputValidator.validateAccountNumber(trimmedAccount, trimmedBank)
    if (accountCheck is ValidationResult.Error) {
        return BankDetailsResult.Invalid(BankDetailsField.ACCOUNT_NUMBER, accountCheck.message)
    }
    val trimmedBranch = branchCode?.trim().orEmpty()
    val branchCheck = InputValidator.validateBranchCode(trimmedBranch)
    if (branchCheck is ValidationResult.Error) {
        return BankDetailsResult.Invalid(BankDetailsField.BRANCH_CODE, branchCheck.message)
    }
    return BankDetailsResult.Valid(
        ValidatedBankDetails(
            bankName = trimmedBank,
            accountNo = trimmedAccount,
            branchCode = trimmedBranch
        )
    )
}
