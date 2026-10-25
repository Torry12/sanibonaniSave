package com.sanibonani.save.features.group.ui.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R
import androidx.compose.ui.unit.dp
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.components.SectionHeader
import com.sanibonani.save.ui.components.BankingDetailsFields

/** Step 4: group banking details. */
@Composable
fun RegStep4(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader(
            title = stringResource(R.string.ui_banking),
            description = stringResource(R.string.ui_where_should_group_funds_be_deposited)
        )

        BankingDetailsFields(
            bankName = s.bankName,
            accountNo = s.accountNumber,
            branchCode = s.branchCode,
            onBankNameChange = { vm.onEvent(GroupFormEvent.BankNameSelected(it)) },
            onAccountNoChange = { vm.onEvent(GroupFormEvent.AccountNumberChanged(it)) },
            onBranchCodeChange = { vm.onEvent(GroupFormEvent.BranchCodeChanged(it)) },
            bankError = s.fieldErrors["bankName"],
            accountError = s.fieldErrors["accountNumber"],
            branchError = s.fieldErrors["branchCode"]
        )
    }
}
