package com.sanibonani.save.domain.architecture

import com.sanibonani.save.domain.model.GroupType

/**
 * Non-breaking mapping from existing runtime GroupType values to broader
 * architecture model categories.
 */
fun GroupType.toFinancialGroupModel(): FinancialGroupModel = when (this) {
    GroupType.ROSCA -> FinancialGroupModel.ROSCA
    GroupType.BURIAL_SOCIETY -> FinancialGroupModel.BURIAL_SOCIETY
    GroupType.INVESTMENT_CLUB -> FinancialGroupModel.INVESTMENT_GROUP
    GroupType.EMERGENCY_FUND -> FinancialGroupModel.EMERGENCY_FUND
    GroupType.STOKVEL -> FinancialGroupModel.ASCA
    GroupType.COMMUNITY_SAVINGS -> FinancialGroupModel.HYBRID_FINANCIAL_GROUP
    GroupType.TONTINE -> FinancialGroupModel.INVESTMENT_GROUP
    GroupType.OTHER -> FinancialGroupModel.HYBRID_FINANCIAL_GROUP
}

