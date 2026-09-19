package com.sanibonani.save.features.member.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.features.member.presentation.MemberRegistrationViewModel

@Composable
fun RegisterMemberScreen(
    groupId: String,
    onNavigatePayment: (Money) -> Unit,
    onNavigateBack: () -> Unit,
    vm: MemberRegistrationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by vm.registerState.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        vm.initializeRegistration(groupId)
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            ToastUtils.showSuccess(context, "Registration successful")
            if (state.joiningFee.isPositive) {
                onNavigatePayment(state.joiningFee)
            } else {
                // If zero fee, navigate to group selection or home (caller handles navigation)
                // In this flow, we likely want to go straight to dashboard.
                // We'll pass Money.ZERO to indicate a skipped payment screen.
                onNavigatePayment(Money.ZERO)
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
                    TopAppBar(
                        title = { Text("Register to Group") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                    )
                    SoftLoadingOverlay(isLoading = state.isLoading, message = "Loading group settings...")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Cream)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ... existing fields ...
                Text(
                    "Personal Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Navy
                )

                SanibonaniTextField(
                    value = state.fullName,
                    onValueChange = { vm.onFieldChange("fullName", it) },
                    label = "Full Name",
                    placeholder = "As on ID document",
                    isError = state.fieldErrors.containsKey("fullName"),
                    errorMessage = state.fieldErrors["fullName"]
                )

                SanibonaniTextField(
                    value = state.idNumber,
                    onValueChange = { vm.onFieldChange("idNumber", it) },
                    label = "SA ID Number",
                    placeholder = "13 digits",
                    isError = state.fieldErrors.containsKey("idNumber"),
                    errorMessage = state.fieldErrors["idNumber"]
                )

                SanibonaniTextField(
                    value = state.phone,
                    onValueChange = { vm.onFieldChange("phone", it) },
                    label = "Phone Number",
                    placeholder = "071 234 5678",
                    isError = state.fieldErrors.containsKey("phone"),
                    errorMessage = state.fieldErrors["phone"]
                )

                SanibonaniTextField(
                    value = state.email,
                    onValueChange = { vm.onFieldChange("email", it) },
                    label = "Email Address",
                    placeholder = "you@example.com",
                    isError = state.fieldErrors.containsKey("email"),
                    errorMessage = state.fieldErrors["email"]
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Residential Address",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Navy
                )

                AutoCompleteTextField(
                    value = state.street,
                    onValueChange = { vm.onFieldChange("street", it) },
                    label = "Street Address",
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
                    label = "Suburb",
                    placeholder = "",
                    isError = state.fieldErrors.containsKey("suburb"),
                    errorMessage = state.fieldErrors["suburb"]
                )

                SanibonaniTextField(
                    value = state.city,
                    onValueChange = { vm.onFieldChange("city", it) },
                    label = "City",
                    placeholder = "",
                    isError = state.fieldErrors.containsKey("city"),
                    errorMessage = state.fieldErrors["city"]
                )

                SanibonaniTextField(
                    value = state.province,
                    onValueChange = { vm.onFieldChange("province", it) },
                    label = "Province",
                    placeholder = "",
                    isError = state.fieldErrors.containsKey("province"),
                    errorMessage = state.fieldErrors["province"]
                )

                Spacer(Modifier.height(24.dp))

                if (state.error != null) {
                    InfoBox(state.error!!, InfoType.ERROR)
                }

                val buttonText = if (state.joiningFee.isPositive) {
                    "Proceed to Payment (${state.joiningFee.format()})"
                } else {
                    "Complete Registration"
                }

                SanibonaniButton(
                    text = buttonText,
                    onClick = { vm.submit(groupId, null) },
                    isLoading = state.isSubmitting,
                    enabled = state.canSubmit && !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
