package com.sanibonani.save.features.group.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.components.SectionHeader
import com.sanibonani.save.features.group.ui.components.SummaryRow
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.utils.IDNumberTransformation

/** Step 6: administrator credentials and registration summary. */
@Composable
fun RegStep5(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader(
            title = stringResource(R.string.ui_admin_account),
            description = stringResource(R.string.ui_create_your_group_administrator_credenti)
        )

        SanibonaniTextField(
            value = s.adminFullName,
            onValueChange = { vm.onEvent(GroupFormEvent.AdminFullNameChanged(it)) },
            label = stringResource(R.string.ui_admin_full_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            isError = s.fieldErrors.containsKey("adminFullName"),
            supportingText = s.fieldErrors["adminFullName"]
        )

        SanibonaniTextField(
            value = s.adminIdNumber,
            onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) vm.onEvent(GroupFormEvent.AdminIdNumberChanged(it)) },
            label = stringResource(R.string.ui_sa_id_number),
            placeholder = stringResource(R.string.ui_13digit_sa_id),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = IDNumberTransformation(),
            isError = s.fieldErrors.containsKey("adminIdNumber"),
            supportingText = s.fieldErrors["adminIdNumber"]
        )

        SanibonaniTextField(
            value = s.adminEmail,
            onValueChange = { vm.onEvent(GroupFormEvent.AdminEmailChanged(it)) },
            label = stringResource(R.string.ui_admin_email_primary_login),
            // Read-only while it is the signed-in account's email; a blank value must still be
            // editable or the required field could never be satisfied.
            enabled = !s.isLoggedIn || s.adminEmail.isBlank(),
            isError = s.fieldErrors.containsKey("adminEmail"),
            supportingText = s.fieldErrors["adminEmail"]
        )

        if (!s.isLoggedIn) {
            var passwordVisible by remember { mutableStateOf(false) }
            SanibonaniTextField(
                value = s.adminPassword,
                onValueChange = { vm.onEvent(GroupFormEvent.AdminPasswordChanged(it)) },
                label = stringResource(R.string.ui_admin_password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, description)
                    }
                },
                isError = s.fieldErrors.containsKey("adminPassword"),
                supportingText = s.fieldErrors["adminPassword"]
            )
        } else {
            InfoBox(
                "You are already logged in. This group will be linked to your existing account.", type = InfoType.INFO
            )
        }

        InfoBox(
            if (s.platformRegistrationFee.isPositive) {
                "As the group creator, you will automatically be registered as the first member. " +
                    "The ${s.platformRegistrationFee.format()} platform registration fee activates the group and is not added to group savings."
            } else {
                "As the group creator, you will automatically be registered as the first member. " +
                    "No platform registration fee applies, so your group is activated as soon as you finish."
            },
            type = InfoType.INFO
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.ui_summary),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                SummaryRow("Name", s.name)
                SummaryRow("Type", s.type.displayName)
                SummaryRow("Location", "${s.city}, ${s.province}")

                val monthlyMoney = s.monthlyContribution.parseMoneyAmountOrNull()?.let { Money(it) } ?: Money.ZERO
                SummaryRow("Monthly Fee", monthlyMoney.format())

                HorizontalDivider(
                    Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            stringResource(R.string.ui_platform_registration_fee),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (s.platformRegistrationFee.isPositive) {
                                stringResource(R.string.ui_this_is_a_onceoff_fee_to_activate_your_g)
                            } else {
                                "No registration fee applies during this promotion."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (s.platformRegistrationFee.isPositive) s.platformRegistrationFee.format() else "Free",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
