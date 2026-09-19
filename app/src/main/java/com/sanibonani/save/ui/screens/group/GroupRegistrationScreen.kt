package com.sanibonani.save.ui.screens.group

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.screens.payment.PaymentScreen
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.viewmodel.*

@Composable
fun GroupRegistrationScreen(
    onSuccess: (String) -> Unit,
    onBack: () -> Unit,
    vm: GroupRegistrationViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()

    LaunchedEffect(state.success, state.createdGroupId) {
        if (state.success) {
            val gid = state.createdGroupId
            if (gid != null) {
                Toast.makeText(context, "Group registered successfully!", Toast.LENGTH_LONG).show()
                onSuccess(gid)
                vm.resetNavigation()
            }
        }
    }

    if (state.needsPayment) {
        val payVm: PaymentViewModel = hiltViewModel()
        val payState by payVm.state.collectAsStateWithLifecycle()

        PaymentScreen(
            paymentType = "registration",
            amount = PlatformFees.REGISTRATION,
            groupId = state.createdGroupId ?: "new_group",
            onPaymentComplete = {
                vm.finalizeRegistrationAfterPayment(payState.transactionId)
            },
            onPaymentFailed = { /* Handled via Global Error Banner */ },
            onBack = { vm.onEvent(GroupFormEvent.DismissPayment) },
            vm = payVm
        )
    } else {
        Scaffold(
            topBar = {
                SanibonaniTopBar(
                    title = "Register Group",
                    onBack = if (state.success) null else {
                        {
                            if (state.currentStep > 1) {
                                vm.prevStep()
                            } else {
                                onBack()
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Cream)
                    .padding(padding)
                    .imePadding()
                    .padding(24.dp)
            ) {
                if (state.success) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = Navy, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.ui_group_created), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.ui_you_will_be_redirected_shortly), color = Color.Black.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { state.currentStep / state.totalSteps.toFloat() },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = Navy,
                        trackColor = Navy.copy(alpha = 0.1f)
                    )
                    Spacer(Modifier.height(24.dp))

                    Column(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (state.currentStep) {
                                1 -> RegStep1(state, vm)
                                2 -> RegStep2(state, vm)
                                3 -> RegStep3(state, vm)
                                4 -> RegStep4(state, vm)
                                5 -> RegStepConstitution(state, vm)
                                6 -> RegStep5(state, vm)
                            }
                        }
                    }

                    if (state.error != null) {
                        InfoBox(state.error!!, InfoType.ERROR)
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.currentStep > 1) {
                            OutlinedButton(
                                onClick = { clickDebouncer.processClick { vm.prevStep() } },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Navy),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy)
                            ) { Text(stringResource(R.string.ui_back)) }
                        }
                        SanibonaniButton(
                            text = if (state.currentStep == 6) "Pay Registration Fee" else "Next",
                            onClick = {
                                clickDebouncer.processClick {
                                    if (state.currentStep == 6) vm.submitGroup() else vm.nextStep()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            isLoading = state.isSubmitting
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegStep1(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Text(stringResource(R.string.ui_basic_info), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
    Text(stringResource(R.string.ui_tell_us_the_name_and_type_of_your_group), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(s.name, { vm.onEvent(GroupFormEvent.NameChanged(it)) }, "Group Name *")
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(s.adminEmail, { vm.onEvent(GroupFormEvent.AdminEmailChanged(it)) }, "Group Email *")
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.adminPhone,
        onValueChange = { if (it.length <= 10) vm.onEvent(GroupFormEvent.AdminPhoneChanged(it)) },
        label = stringResource(R.string.ui_whatsapp_number_1),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        visualTransformation = PhoneNumberTransformation()
    )
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.ui_group_type), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    GroupType.entries.forEach { type ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.onEvent(GroupFormEvent.TypeSelected(type)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = s.type == type, onClick = { vm.onEvent(GroupFormEvent.TypeSelected(type)) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(type.displayName, fontWeight = FontWeight.Medium)
                Text(getDesc(type), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
            }
        }
    }

    if (s.type == GroupType.ROSCA) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.ui_rosca_rotation_method), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        RoscaRotationMethod.entries.forEach { method ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.onEvent(GroupFormEvent.RoscaRotationMethodSelected(method)) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = s.rotationMethod == method, onClick = { vm.onEvent(GroupFormEvent.RoscaRotationMethodSelected(method)) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(method.displayName, fontWeight = FontWeight.Medium)
                    Text(method.description, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.ui_group_icon), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.ui_choose_an_emoji_to_represent_your_group), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(12.dp))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(64.dp).background(Navy.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(s.logoEmoji, fontSize = 32.sp)
        }

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🤝", "💰", "🏠", "📈", "🕊️", "🛡️", "🇿🇦", "🔥", "💎", "🌟").forEach { emoji ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { vm.onEvent(GroupFormEvent.LogoEmojiSelected(emoji)) }
                        .background(if (s.logoEmoji == emoji) Navy.copy(alpha = 0.2f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

private fun getDesc(t: GroupType) = when(t) {
    GroupType.STOKVEL -> "Traditional savings and social event support."
    GroupType.BURIAL_SOCIETY -> "Dedicated funds for funeral expenses."
    GroupType.INVESTMENT_CLUB -> "Pooling capital for long-term growth."
    GroupType.ROSCA -> "Rotating Savings and Credit Association."
    GroupType.EMERGENCY_FUND -> "Savings for unexpected costs."
    GroupType.COMMUNITY_SAVINGS -> "General community savings group."
    GroupType.TONTINE -> "Investment system where participants share a common fund."
    GroupType.OTHER -> "Other type of savings group."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegStep2(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Text(stringResource(R.string.ui_location), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
    Text(stringResource(R.string.ui_where_is_your_group_based), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))

    AutoCompleteTextField(
        value = s.city,
        onValueChange = { vm.onEvent(GroupFormEvent.CityChanged(it)) },
        label = stringResource(R.string.ui_city_town_township),
        suggestions = s.addressSuggestions,
        onSuggestionClick = { vm.onEvent(GroupFormEvent.AddressSelected(it)) },
        isLoading = s.isSearchingAddress
    )
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.ui_province_1), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SaReferenceData.PROVINCES.forEach { p ->
            FilterChip(
                selected = s.province == p,
                onClick = { vm.onEvent(GroupFormEvent.ProvinceSelected(p)) },
                label = { Text(p) }
            )
        }
    }
}

@Composable
private fun RegStep3(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Text(stringResource(R.string.ui_rules_fees), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
    Text(stringResource(R.string.ui_define_the_financial_commitments), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.joiningFee,
        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.JoiningFeeChanged(it)) },
        label = stringResource(R.string.ui_registration_joining_fee),
        placeholder = stringResource(R.string.ui_eg_50000),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.monthlyContribution,
        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.MonthlyContributionChanged(it)) },
        label = stringResource(R.string.ui_monthly_contribution),
        placeholder = stringResource(R.string.ui_eg_20000),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.maxMembers,
        onValueChange = { vm.onEvent(GroupFormEvent.MaxMembersChanged(it.filter { c -> c.isDigit() })) },
        label = stringResource(R.string.ui_maximum_members_1),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.lateFee,
        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.LateFeeChanged(it)) },
        label = stringResource(R.string.ui_late_payment_fine),
        placeholder = stringResource(R.string.ui_eg_5000),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.lateFeeGraceDays,
        onValueChange = { vm.onEvent(GroupFormEvent.LateFeeGraceDaysChanged(it.filter { c -> c.isDigit() })) },
        label = stringResource(R.string.ui_grace_period_days),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.probationMonths,
        onValueChange = { vm.onEvent(GroupFormEvent.ProbationMonthsChanged(it.filter { c -> c.isDigit() })) },
        label = stringResource(R.string.ui_probation_period_months),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.paymentDueDay,
        onValueChange = { vm.onEvent(GroupFormEvent.PaymentDueDayChanged(it.filter { c -> c.isDigit() })) },
        label = stringResource(R.string.ui_payment_due_day_of_month),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    if (s.type == GroupType.STOKVEL || s.type == GroupType.INVESTMENT_CLUB) {
        SanibonaniTextField(
            value = s.goalAmount,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.GoalAmountChanged(it)) },
            label = stringResource(R.string.ui_total_savings_goal_r),
            placeholder = stringResource(R.string.ui_eg_5000000),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(16.dp))

        SanibonaniTextField(
            value = s.periodMonths,
            onValueChange = { vm.onEvent(GroupFormEvent.PeriodMonthsChanged(it.filter { c -> c.isDigit() })) },
            label = stringResource(R.string.ui_savings_period_months),
            placeholder = stringResource(R.string.ui_eg_12),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(16.dp))
    }

    if (s.type == GroupType.BURIAL_SOCIETY) {
        SanibonaniTextField(
            value = s.maxBeneficiaries,
            onValueChange = { vm.onEvent(GroupFormEvent.MaxBeneficiariesChanged(it.filter { c -> c.isDigit() })) },
            label = stringResource(R.string.ui_max_beneficiaries_per_member_1),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(16.dp))

        SanibonaniTextField(
            value = s.beneficiaryIncreasePct,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.BeneficiaryIncreasePctChanged(it)) },
            label = stringResource(R.string.ui_monthly_increase_per_extra_beneficiary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(16.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = s.allowPartialPayment,
            onCheckedChange = { vm.onEvent(GroupFormEvent.AllowPartialPaymentToggled(it)) },
            colors = CheckboxDefaults.colors(checkedColor = Navy)
        )
        Column {
            Text(stringResource(R.string.ui_allow_partial_payments), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ui_if_disabled_members_must_pay_the_full_mo_1),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = s.termsAccepted,
            onCheckedChange = { vm.onEvent(GroupFormEvent.TermsAcceptedToggled(it)) },
            colors = CheckboxDefaults.colors(checkedColor = Navy)
        )
        Text(stringResource(R.string.ui_i_accept_the_terms_and_conditions))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegStep4(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Text(stringResource(R.string.ui_banking), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
    Text(stringResource(R.string.ui_where_should_group_funds_be_deposited), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = s.bankName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.ui_bank_name_1)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Navy, focusedLabelColor = Navy)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SaReferenceData.BANKS.forEach { b ->
                DropdownMenuItem(text = { Text(b) }, onClick = {
                    vm.onEvent(GroupFormEvent.BankNameSelected(b))
                    expanded = false
                })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SanibonaniTextField(
        s.accountNumber,
        { vm.onEvent(GroupFormEvent.AccountNumberChanged(it.filter { c -> c.isDigit() })) },
        "Account Number *",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(12.dp))
    SanibonaniTextField(
        s.branchCode,
        { vm.onEvent(GroupFormEvent.BranchCodeChanged(it.filter { c -> c.isDigit() })) },
        "Branch Code *",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun RegStepConstitution(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Text(stringResource(R.string.ui_constitution), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
    Text(stringResource(R.string.ui_upload_your_groups_constitution_or_rules), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                val fileName = "constitution_${System.currentTimeMillis()}.pdf"
                if (bytes != null) {
                    if (bytes.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
                        ToastUtils.showError(context, "File too large. Maximum 3MB allowed.")
                    } else {
                        vm.uploadConstitution(bytes, fileName)
                    }
                }
            } catch (_: Exception) {
                ToastUtils.showError(context, "Failed to read the selected file. Please try another PDF.")
            }
        }
    }

    DocumentUploadCard(
        name = "Group Constitution",
        isUploaded = s.constitutionUrl != null || s.pendingConstitutionName != null,
        status = if (s.pendingConstitutionName != null) DocumentStatus.PENDING else s.constitutionStatus,
        onUpload = { launcher.launch("application/pdf") }
    )

    Spacer(Modifier.height(16.dp))

    Surface(
        onClick = { vm.onEvent(GroupFormEvent.UseStandardConstitutionToggled(!s.useStandardConstitution)) },
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = s.useStandardConstitution,
                onCheckedChange = { vm.onEvent(GroupFormEvent.UseStandardConstitutionToggled(it)) },
                colors = CheckboxDefaults.colors(checkedColor = Navy)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.ui_use_standard_constitution), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_generate_a_professional_policy_document_), style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f))
            }
        }
    }

    if (s.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Navy)
    }

    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.ui_why_is_this_needed), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.ui_a_constitution_helps_resolve_disputes_an),
        style = MaterialTheme.typography.bodySmall,
        color = Color.Black.copy(alpha = 0.6f)
    )
}

@Composable
private fun RegStep5(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Text(stringResource(R.string.ui_admin_account), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
    Text(stringResource(R.string.ui_create_your_group_administrator_credenti), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.adminFullName,
        onValueChange = { vm.onEvent(GroupFormEvent.AdminFullNameChanged(it)) },
        label = stringResource(R.string.ui_admin_full_name),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
    )
    Spacer(Modifier.height(12.dp))

    SanibonaniTextField(
        value = s.adminIdNumber,
        onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) vm.onEvent(GroupFormEvent.AdminIdNumberChanged(it)) },
        label = stringResource(R.string.ui_sa_id_number),
        placeholder = stringResource(R.string.ui_13digit_sa_id),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = IDNumberTransformation(),
        isError = s.adminIdNumber.isNotEmpty() && s.adminIdNumber.length != 13,
        errorMessage = if (s.adminIdNumber.isNotEmpty() && s.adminIdNumber.length != 13) "ID Number must be 13 digits" else null
    )
    Spacer(Modifier.height(12.dp))

    SanibonaniTextField(
        value = s.adminEmail,
        onValueChange = { vm.onEvent(GroupFormEvent.AdminEmailChanged(it)) },
        label = stringResource(R.string.ui_admin_email_primary_login),
        enabled = !s.isLoggedIn
    )
    Spacer(Modifier.height(12.dp))

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
            }
        )
    } else {
        InfoBox("You are already logged in. This group will be linked to your existing account.", InfoType.INFO)
    }

    Spacer(Modifier.height(24.dp))
    InfoBox(
        "As the group creator, you will automatically be registered as the first member. " +
        "Your ${PlatformFees.REGISTRATION.format()} platform registration fee will be credited as your first contribution.",
        InfoType.INFO
    )

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.ui_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Name: ${s.name}", color = Color.Black)
            Text("Type: ${s.type.displayName}", color = Color.Black)
            Text("Location: ${s.city}, ${s.province}", color = Color.Black)
            val monthlyMoney = s.monthlyContribution.parseMoneyAmountOrNull()?.let { com.sanibonani.save.domain.model.Money(it) } ?: com.sanibonani.save.domain.model.Money.ZERO
            Text("Monthly Fee: ${monthlyMoney.format()}", color = Color.Black)

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Black.copy(alpha = 0.1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ui_platform_registration_fee), style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.6f))
                Text(PlatformFees.REGISTRATION.format(), fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Text(stringResource(R.string.ui_this_is_a_onceoff_fee_to_activate_your_g), style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f))
        }
    }
}
