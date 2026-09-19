package com.sanibonani.save.features.group.ui

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
import com.sanibonani.save.features.financial.ui.PaymentScreen
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.financial.presentation.PaymentViewModel
import com.sanibonani.save.features.group.presentation.RegisterGroupValidator
import com.sanibonani.save.viewmodel.GroupFormEvent

@Composable
fun RegisterGroupScreen(onGroupCreated: (String) -> Unit, onBack: () -> Unit) {
    GroupRegistrationScreen(onSuccess = onGroupCreated, onBack = onBack)
}

@Composable
fun GroupRegistrationScreen(
    onSuccess: (String) -> Unit,
    onBack: () -> Unit,
    vm: GroupRegistrationViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()
    val payVm: PaymentViewModel = hiltViewModel()

    // ── Fresh Form Hardening ──
    // Ensure the form is fresh when entering from a neutral state
    LaunchedEffect(Unit) {
        if (state.createdGroupId == null && !state.success) {
            vm.resetNavigation()
        }
    }

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
    } else if (state.isSubmitting && !state.success) {
        SanibonaniLoadingScreen(message = "Creating your group...")
    } else {
        Scaffold(
            topBar = {
                Column {
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
                    SoftLoadingOverlay(isLoading = state.isLoading, message = "Updating fee configurations...")
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .imePadding()
                    .padding(24.dp)
            ) {
                if (state.success) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.ui_group_created), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.ui_you_will_be_redirected_shortly), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val progress by remember { derivedStateOf { state.currentStep / state.totalSteps.toFloat() } }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    Spacer(Modifier.height(24.dp))

                    Column(Modifier.weight(1f)) {
                        val currentStep = state.currentStep
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            AnimatedStepTransition(currentStep = currentStep) { step ->
                                when (step) {
                                    1 -> RegStep1(state, vm)
                                    2 -> RegStep2(state, vm)
                                    3 -> RegStep3(state, vm)
                                    4 -> RegStep4(state, vm)
                                    5 -> RegStepConstitution(state, vm)
                                    6 -> RegStep5(state, vm)
                                }
                            }
                        }
                    }

                    state.error?.let { error ->
                        InfoBox(error, InfoType.ERROR)
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.currentStep > 1) {
                            OutlinedButton(
                                onClick = { clickDebouncer.processClick { vm.prevStep() } },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_basic_info), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_tell_us_the_name_and_type_of_your_group), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SanibonaniTextField(
            value = s.name,
            onValueChange = { vm.onEvent(GroupFormEvent.NameChanged(it)) },
            label = "Group Name *",
            isError = s.fieldErrors.containsKey("name"),
            errorMessage = s.fieldErrors["name"]
        )

        SanibonaniTextField(
            value = s.adminEmail,
            onValueChange = { vm.onEvent(GroupFormEvent.AdminEmailChanged(it)) },
            label = "Group Email *",
            isError = s.fieldErrors.containsKey("adminEmail"),
            errorMessage = s.fieldErrors["adminEmail"]
        )

        SanibonaniTextField(
            value = s.adminPhone,
            onValueChange = { if (it.length <= 10) vm.onEvent(GroupFormEvent.AdminPhoneChanged(it)) },
            label = stringResource(R.string.ui_primary_contact_number_required),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneNumberTransformation(),
            isError = s.fieldErrors.containsKey("adminPhone"),
            errorMessage = s.fieldErrors["adminPhone"]
        )

        Text(stringResource(R.string.ui_group_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        GroupType.entries.forEach { type ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.onEvent(GroupFormEvent.TypeSelected(type)) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = s.type == type,
                    onClick = { vm.onEvent(GroupFormEvent.TypeSelected(type)) },
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(type.displayName, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(getDesc(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (s.type == GroupType.ROSCA) {
            Text(stringResource(R.string.ui_rosca_rotation_method), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            RoscaRotationMethod.entries.forEach { method ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.onEvent(GroupFormEvent.RoscaRotationMethodSelected(method)) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = s.rotationMethod == method,
                        onClick = { vm.onEvent(GroupFormEvent.RoscaRotationMethodSelected(method)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(method.displayName, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text(stringResource(R.string.ui_group_icon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_choose_an_emoji_to_represent_your_group), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupIcon(
                logoEmoji = s.logoEmoji,
                size = 64.dp,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🤝", "💰", "🏠", "📈", "🕊️", "🛡️", "🇿🇦", "🔥", "💎", "🌟").forEach { emoji ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { vm.onEvent(GroupFormEvent.LogoEmojiSelected(emoji)) }
                            .background(if (s.logoEmoji == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_location), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_where_is_your_group_based), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        AutoCompleteTextField(
            value = s.city,
            onValueChange = { vm.onEvent(GroupFormEvent.CityChanged(it)) },
            label = stringResource(R.string.ui_city_town_township),
            suggestions = s.addressSuggestions,
            onSuggestionClick = { vm.onEvent(GroupFormEvent.AddressSelected(it)) },
            isLoading = s.isSearchingAddress,
            isError = s.fieldErrors.containsKey("city"),
            errorMessage = s.fieldErrors["city"]
        )

        Text(stringResource(R.string.ui_province_1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaReferenceData.PROVINCES.forEach { p ->
                FilterChip(
                    selected = s.province == p,
                    onClick = { vm.onEvent(GroupFormEvent.ProvinceSelected(p)) },
                    label = { Text(p) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun RegStep3(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_rules_fees), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_define_the_financial_commitments), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SanibonaniTextField(
            value = s.joiningFee,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.JoiningFeeChanged(it)) },
            label = stringResource(R.string.ui_registration_joining_fee),
            placeholder = stringResource(R.string.ui_eg_50000),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = s.fieldErrors.containsKey("joiningFee"),
            errorMessage = s.fieldErrors["joiningFee"]
        )

        SanibonaniTextField(
            value = s.monthlyContribution,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.MonthlyContributionChanged(it)) },
            label = stringResource(R.string.ui_monthly_contribution),
            placeholder = stringResource(R.string.ui_eg_20000),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = s.fieldErrors.containsKey("monthlyContribution"),
            errorMessage = s.fieldErrors["monthlyContribution"]
        )

        if (s.type != GroupType.ROSCA) {
            SanibonaniTextField(
                value = s.maxMembers,
                onValueChange = { vm.onEvent(GroupFormEvent.MaxMembersChanged(it.filter { c -> c.isDigit() })) },
                label = stringResource(R.string.ui_maximum_members_1),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SanibonaniTextField(
            value = s.lateFee,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.LateFeeChanged(it)) },
            label = stringResource(R.string.ui_late_payment_fine),
            placeholder = stringResource(R.string.ui_eg_5000),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        SanibonaniTextField(
            value = s.lateFeeGraceDays,
            onValueChange = { vm.onEvent(GroupFormEvent.LateFeeGraceDaysChanged(it.filter { c -> c.isDigit() })) },
            label = stringResource(R.string.ui_grace_period_days),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        SanibonaniTextField(
            value = s.probationMonths,
            onValueChange = { vm.onEvent(GroupFormEvent.ProbationMonthsChanged(it.filter { c -> c.isDigit() })) },
            label = stringResource(R.string.ui_probation_period_months),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        SanibonaniTextField(
            value = s.paymentDueDay,
            onValueChange = { vm.onEvent(GroupFormEvent.PaymentDueDayChanged(it.filter { c -> c.isDigit() })) },
            label = stringResource(R.string.ui_payment_due_day_of_month),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        if (s.type == GroupType.ROSCA) {
            Text("ROSCA Cycle Duration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Select the number of months for a full rotation cycle.", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 12).forEach { months ->
                    FilterChip(
                        selected = s.roscaCycle == months,
                        onClick = { vm.onEvent(GroupFormEvent.RoscaCycleChanged(months)) },
                        label = { Text("$months Months") }
                    )
                }
            }
            InfoBox("For ROSCA, the number of members will be fixed to ${s.roscaCycle} to complete the cycle.", InfoType.INFO)
        }

        if (s.type == GroupType.STOKVEL || s.type == GroupType.INVESTMENT_CLUB) {
            SanibonaniTextField(
                value = s.goalAmount,
                onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.GoalAmountChanged(it)) },
                label = stringResource(R.string.ui_total_savings_goal_r),
                placeholder = stringResource(R.string.ui_eg_5000000),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            SanibonaniTextField(
                value = s.periodMonths,
                onValueChange = { vm.onEvent(GroupFormEvent.PeriodMonthsChanged(it.filter { c -> c.isDigit() })) },
                label = stringResource(R.string.ui_savings_period_months),
                placeholder = stringResource(R.string.ui_eg_12),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        if (s.type == GroupType.BURIAL_SOCIETY) {
            SanibonaniTextField(
                value = s.maxBeneficiaries,
                onValueChange = { vm.onEvent(GroupFormEvent.MaxBeneficiariesChanged(it.filter { c -> c.isDigit() })) },
                label = stringResource(R.string.ui_max_beneficiaries_per_member_1),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SanibonaniTextField(
                value = s.beneficiaryIncreasePct,
                onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.onEvent(GroupFormEvent.BeneficiaryIncreasePctChanged(it)) },
                label = stringResource(R.string.ui_monthly_increase_per_extra_beneficiary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = s.allowPartialPayment,
                onCheckedChange = { vm.onEvent(GroupFormEvent.AllowPartialPaymentToggled(it)) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Column {
                Text(stringResource(R.string.ui_allow_partial_payments), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.ui_if_disabled_members_must_pay_the_full_mo_1),
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
            Text(stringResource(R.string.ui_i_accept_the_terms_and_conditions), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegStep4(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_banking), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_where_should_group_funds_be_deposited), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

@Composable
private fun RegStepConstitution(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_constitution), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_upload_your_groups_constitution_or_rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
            label = "Group Constitution",
            isUploaded = s.constitutionUrl != null || s.pendingConstitutionName != null,
            status = if (s.pendingConstitutionName != null) DocumentStatus.PENDING else s.constitutionStatus,
            onUpload = { launcher.launch("application/pdf") }
        )

        Surface(
            onClick = { vm.onEvent(GroupFormEvent.UseStandardConstitutionToggled(!s.useStandardConstitution)) },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = s.useStandardConstitution,
                    onCheckedChange = { vm.onEvent(GroupFormEvent.UseStandardConstitutionToggled(it)) },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.ui_use_standard_constitution), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.ui_generate_a_professional_policy_document_), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (s.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
        }

        Column {
            Text(stringResource(R.string.ui_why_is_this_needed), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.ui_a_constitution_helps_resolve_disputes_an),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RegStep5(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_admin_account), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_create_your_group_administrator_credenti), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SanibonaniTextField(
            value = s.adminFullName,
            onValueChange = { vm.onEvent(GroupFormEvent.AdminFullNameChanged(it)) },
            label = stringResource(R.string.ui_admin_full_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            isError = s.fieldErrors.containsKey("adminFullName"),
            errorMessage = s.fieldErrors["adminFullName"]
        )

        SanibonaniTextField(
            value = s.adminIdNumber,
            onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) vm.onEvent(GroupFormEvent.AdminIdNumberChanged(it)) },
            label = stringResource(R.string.ui_sa_id_number),
            placeholder = stringResource(R.string.ui_13digit_sa_id),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = IDNumberTransformation(),
            isError = s.fieldErrors.containsKey("adminIdNumber"),
            errorMessage = s.fieldErrors["adminIdNumber"]
        )

        SanibonaniTextField(
            value = s.adminEmail,
            onValueChange = { vm.onEvent(GroupFormEvent.AdminEmailChanged(it)) },
            label = stringResource(R.string.ui_admin_email_primary_login),
            enabled = !s.isLoggedIn,
            isError = s.fieldErrors.containsKey("adminEmail"),
            errorMessage = s.fieldErrors["adminEmail"]
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
                errorMessage = s.fieldErrors["adminPassword"]
            )
        } else {
            InfoBox("You are already logged in. This group will be linked to your existing account.", InfoType.INFO)
        }

        InfoBox(
            "As the group creator, you will automatically be registered as the first member. " +
                "Your ${s.platformRegistrationFee.format()} platform registration fee will be credited as your first contribution.",
            InfoType.INFO
        )

        Text(stringResource(R.string.ui_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(Modifier.padding(12.dp)) {
                Text("Name: ${s.name}", color = MaterialTheme.colorScheme.onSurface)
                Text("Type: ${s.type.displayName}", color = MaterialTheme.colorScheme.onSurface)
                Text("Location: ${s.city}, ${s.province}", color = MaterialTheme.colorScheme.onSurface)
                val monthlyMoney = s.monthlyContribution.parseMoneyAmountOrNull()?.let { com.sanibonani.save.domain.model.Money(it) } ?: com.sanibonani.save.domain.model.Money.ZERO
                Text("Monthly Fee: ${monthlyMoney.format()}", color = MaterialTheme.colorScheme.onSurface)

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.ui_platform_registration_fee), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.platformRegistrationFee.format(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.ui_this_is_a_onceoff_fee_to_activate_your_g), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
