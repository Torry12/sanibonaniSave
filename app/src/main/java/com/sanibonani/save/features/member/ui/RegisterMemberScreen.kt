package com.sanibonani.save.features.member.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.presentation.MemberRegistrationViewModel
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.IDNumberTransformation
import com.sanibonani.save.ui.utils.PhoneNumberTransformation
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer

@Composable
fun RegisterMemberScreen(
    groupId: String,
    onNavigatePayment: (Money) -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateBack: () -> Unit,
    vm: MemberRegistrationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.registerState.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    LaunchedEffect(groupId) {
        vm.initializeRegistration(groupId)
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            ToastUtils.showSuccess(context, "Registration successful")
            if (state.joiningFee.isPositive) {
                onNavigatePayment(state.joiningFee)
            } else {
                onNavigateDashboard()
            }
        }
    }

    if (state.isLoading && state.targetGroupId.isNullOrBlank()) {
        SanibonaniLoadingScreen(message = "Fetching group details...")
    } else if (state.isSubmitting && !state.success) {
        SanibonaniLoadingScreen(message = "Registering you in group...")
    } else {
        Scaffold(
            topBar = {
                Column {
                    SanibonaniTopBar(
                        title = stringResource(R.string.ui_join_group),
                        onBack = onNavigateBack
                    )
                    SoftLoadingOverlay(isLoading = state.isLoading, message = "Loading group settings...")
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Section 1: Personal Details
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.ui_personal_details),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SanibonaniTextField(
                            value = state.fullName,
                            onValueChange = { vm.onFieldChange("fullName", it) },
                            label = stringResource(R.string.ui_full_name),
                            placeholder = stringResource(R.string.ui_as_on_id_document),
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            enabled = !state.isLoading,
                            isError = state.fieldErrors.containsKey("fullName"),
                            supportingText = state.fieldErrors["fullName"]
                        )

                        SanibonaniTextField(
                            value = state.idNumber,
                            onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) vm.onFieldChange("idNumber", it) },
                            label = stringResource(R.string.ui_sa_id_number),
                            placeholder = stringResource(R.string.ui_sa_id_number_hint),
                            leadingIcon = { Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            visualTransformation = IDNumberTransformation(),
                            enabled = !state.isLoading,
                            isError = state.fieldErrors.containsKey("idNumber"),
                            supportingText = state.fieldErrors["idNumber"]
                        )

                        SanibonaniTextField(
                            value = state.phone,
                            onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) vm.onFieldChange("phone", it) },
                            label = stringResource(R.string.ui_phone_number),
                            placeholder = stringResource(R.string.ui_phone_number_hint),
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            visualTransformation = PhoneNumberTransformation(),
                            enabled = !state.isLoading,
                            isError = state.fieldErrors.containsKey("phone"),
                            supportingText = state.fieldErrors["phone"]
                        )

                        SanibonaniTextField(
                            value = state.email,
                            onValueChange = { vm.onFieldChange("email", it) },
                            label = stringResource(R.string.ui_email_address),
                            placeholder = stringResource(R.string.ui_email_hint),
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            enabled = !state.isLoading,
                            isError = state.fieldErrors.containsKey("email"),
                            supportingText = state.fieldErrors["email"]
                        )
                    }
                    // Section 2: Residential Address
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.ui_residential_address),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        AutoCompleteTextField(
                            value = state.street,
                            onValueChange = { vm.onFieldChange("street", it) },
                            label = stringResource(R.string.ui_street_address),
                            enabled = !state.isLoading,
                            suggestions = state.addressSuggestions,
                            onSuggestionClick = { address ->
                                vm.onAddressSelected(address)
                            },
                            isLoading = state.isSearchingAddress,
                            isError = state.fieldErrors.containsKey("street"),
                            errorMessage = state.fieldErrors["street"]
                        )

                        SanibonaniTextField(
                            value = state.suburb,
                            onValueChange = { vm.onFieldChange("suburb", it) },
                            label = stringResource(R.string.ui_suburb),
                            leadingIcon = { Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary) },
                            enabled = !state.isLoading,
                            isError = state.fieldErrors.containsKey("suburb"),
                            supportingText = state.fieldErrors["suburb"]
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SanibonaniTextField(
                                value = state.city,
                                onValueChange = { vm.onFieldChange("city", it) },
                                label = stringResource(R.string.ui_city),
                                leadingIcon = { Icon(Icons.Default.LocationCity, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                isError = state.fieldErrors.containsKey("city"),
                                supportingText = state.fieldErrors["city"]
                            )

                            SanibonaniTextField(
                                value = state.province,
                                onValueChange = { vm.onFieldChange("province", it) },
                                label = stringResource(R.string.ui_province),
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                enabled = !state.isLoading,
                                isError = state.fieldErrors.containsKey("province"),
                                supportingText = state.fieldErrors["province"]
                            )
                        }
                    }

                    val errorMessage = state.error
                    if (errorMessage != null) {
                        InfoBox(errorMessage, type = InfoType.ERROR)
                    }

                    val buttonText = if (state.joiningFee.isPositive) {
                        "Join and Pay ${state.joiningFee.format()}"
                    } else {
                        "Complete Registration"
                    }

                    SanibonaniButton(
                        text = buttonText,
                        onClick = {
                            clickDebouncer.processClick { vm.submit(groupId, null) }
                        },
                        isLoading = state.isSubmitting,
                        enabled = state.canSubmit && !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
