package com.sanibonani.save.features.group.ui.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.components.SectionHeader
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType

/**
 * Step 3: financial commitments, group rules, penalties, type-specific limits
 * and the terms/partial-payment checkboxes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegStep3(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {

        SectionHeader(
            title = "Financial Commitments",
            description = stringResource(R.string.ui_define_the_financial_commitments)
        )

        SanibonaniTextField(
            value = s.joiningFee,
            onValueChange = { if (it.isEmpty() || it.toBigDecimalOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.JoiningFeeChanged(it)) },
            label = "Joining Fee (R)",
            placeholder = "e.g. 150",
            prefix = { Text("R ", color = MaterialTheme.colorScheme.onSurface) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = s.fieldErrors.containsKey("joiningFee"),
            supportingText = s.fieldErrors["joiningFee"]
        )

        SanibonaniTextField(
            value = s.monthlyContribution,
            onValueChange = { if (it.isEmpty() || it.toBigDecimalOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.MonthlyContributionChanged(it)) },
            label = "Monthly Contribution (R)",
            placeholder = "e.g. 500",
            prefix = { Text("R ", color = MaterialTheme.colorScheme.onSurface) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = s.fieldErrors.containsKey("monthlyContribution"),
            supportingText = s.fieldErrors["monthlyContribution"]
        )

        if (s.type == GroupType.STOKVEL || s.type == GroupType.INVESTMENT_CLUB) {
            SanibonaniTextField(
                value = s.goalAmount,
                onValueChange = { if (it.isEmpty() || it.toBigDecimalOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.GoalAmountChanged(it)) },
                label = "Total Savings Goal (R)",
                placeholder = "e.g. 50000",
                prefix = { Text("R ", color = MaterialTheme.colorScheme.onSurface) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            SanibonaniTextField(
                value = s.periodMonths,
                onValueChange = { vm.onEvent(GroupFormEvent.PeriodMonthsChanged(it.filter { c -> c.isDigit() })) },
                label = "Savings Period (Months)",
                placeholder = "e.g. 12",
                suffix = { Text("Months", color = MaterialTheme.colorScheme.onSurface) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SectionHeader(title = "Group Rules & Penalties")

        if (s.type != GroupType.ROSCA) {
            SanibonaniTextField(
                value = s.maxMembers,
                onValueChange = { vm.onEvent(GroupFormEvent.MaxMembersChanged(it.filter { c -> c.isDigit() })) },
                label = "Maximum Members",
                placeholder = "e.g. 50",
                suffix = { Text("Members", color = MaterialTheme.colorScheme.onSurface) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SanibonaniTextField(
            value = s.lateFee,
            onValueChange = { if (it.isEmpty() || it.toBigDecimalOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.LateFeeChanged(it)) },
            label = "Late Payment Fine (R)",
            placeholder = "e.g. 50",
            prefix = { Text("R ", color = MaterialTheme.colorScheme.onSurface) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        SanibonaniTextField(
            value = s.lateFeeGraceDays,
            onValueChange = { vm.onEvent(GroupFormEvent.LateFeeGraceDaysChanged(it.filter { c -> c.isDigit() })) },
            label = "Grace Period (Days)",
            placeholder = "e.g. 5",
            suffix = { Text("Days", color = MaterialTheme.colorScheme.onSurface) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        SanibonaniTextField(
            value = s.probationMonths,
            onValueChange = { vm.onEvent(GroupFormEvent.ProbationMonthsChanged(it.filter { c -> c.isDigit() })) },
            label = "Probation Period (Months)",
            placeholder = "e.g. 1",
            suffix = { Text("Months", color = MaterialTheme.colorScheme.onSurface) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        SanibonaniTextField(
            value = s.paymentDueDay,
            onValueChange = { vm.onEvent(GroupFormEvent.PaymentDueDayChanged(it.filter { c -> c.isDigit() })) },
            label = stringResource(R.string.ui_payment_due_day_of_month),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        if (s.type == GroupType.ROSCA) {
            SectionHeader(
                title = "ROSCA Cycle Duration",
                description = "Select the number of months for a full rotation cycle."
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(3, 6, 12).forEach { months ->
                    val isSelected = s.roscaCycle == months
                    FilterChip(
                        selected = isSelected,
                        onClick = { vm.onEvent(GroupFormEvent.RoscaCycleChanged(months)) },
                        label = { Text("$months Months", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            InfoBox(
                "For ROSCA, the number of members will be fixed to ${s.roscaCycle} to complete the cycle.", type = InfoType.INFO
            )
        }

        if (s.type == GroupType.BURIAL_SOCIETY) {
            SectionHeader(title = "Beneficiary Settings")
            SanibonaniTextField(
                value = s.maxBeneficiaries,
                onValueChange = { vm.onEvent(GroupFormEvent.MaxBeneficiariesChanged(it.filter { c -> c.isDigit() })) },
                label = stringResource(R.string.ui_max_beneficiaries_per_member_1),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SanibonaniTextField(
                value = s.beneficiaryIncreasePct,
                onValueChange = { if (it.isEmpty() || it.toBigDecimalOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.BeneficiaryIncreasePctChanged(it)) },
                label = stringResource(R.string.ui_monthly_increase_per_extra_beneficiary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = s.allowPartialPayment,
                        onCheckedChange = { vm.onEvent(GroupFormEvent.AllowPartialPaymentToggled(it)) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.ui_allow_partial_payments),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.ui_if_disabled_members_must_pay_the_full_mo_1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = s.termsAccepted,
                        onCheckedChange = { vm.onEvent(GroupFormEvent.TermsAcceptedToggled(it)) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ui_i_accept_the_terms_and_conditions),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
