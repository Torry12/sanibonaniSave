package com.sanibonani.save.ui.screens.member

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.Cream
import com.sanibonani.save.ui.utils.IDNumberTransformation
import com.sanibonani.save.ui.utils.PhoneNumberTransformation
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.viewmodel.MemberViewModel

@Composable
fun RegisterMemberScreen(
    groupId: String,
    onMemberRegistered: (Money) -> Unit,
    onBack: () -> Unit,
    vm: MemberViewModel = hiltViewModel()
) {
    val state by vm.registerState.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    LaunchedEffect(groupId) {
        vm.initializeRegistration(groupId)
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            onMemberRegistered(state.joiningFee)
        }
    }

    Scaffold(
        topBar = { SanibonaniTopBar("Member Registration", onBack = { clickDebouncer.processClick(onBack) }) },
        containerColor = Cream
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.ui_join_group), style = MaterialTheme.typography.headlineMedium, color = Color.Black)
            Text(stringResource(R.string.ui_please_provide_your_details_to_join_this), color = Color.Black.copy(alpha = 0.6f))

            SanibonaniTextField(
                value = state.fullName,
                onValueChange = { vm.onFieldChange("fullName", it) },
                label = stringResource(R.string.ui_full_name),
                placeholder = stringResource(R.string.ui_john_doe),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            SanibonaniTextField(
                value = state.idNumber,
                onValueChange = { if (it.length <= 13) vm.onFieldChange("idNumber", it) },
                label = stringResource(R.string.ui_sa_id_number_1),
                placeholder = stringResource(R.string.ui_yymmdd_ssss_caz),
                visualTransformation = IDNumberTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SanibonaniTextField(
                value = state.phone,
                onValueChange = { if (it.length <= 10) vm.onFieldChange("phone", it) },
                label = stringResource(R.string.ui_phone_number),
                placeholder = stringResource(R.string.ui_071_234_5678),
                visualTransformation = PhoneNumberTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            SanibonaniTextField(
                value = state.email,
                onValueChange = { vm.onFieldChange("email", it) },
                label = stringResource(R.string.ui_email_address),
                placeholder = stringResource(R.string.ui_youexamplecom),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
            Text(stringResource(R.string.ui_residential_address), style = MaterialTheme.typography.titleMedium, color = Color.Black)

            AutoCompleteTextField(
                value = state.street,
                onValueChange = { vm.onFieldChange("street", it) },
                label = stringResource(R.string.ui_street_address),
                suggestions = state.addressSuggestions,
                onSuggestionClick = { vm.onAddressSelected(it) },
                isLoading = state.isSearchingAddress
            )

            SanibonaniTextField(
                value = state.suburb,
                onValueChange = { vm.onFieldChange("suburb", it) },
                label = stringResource(R.string.ui_suburb),
                placeholder = stringResource(R.string.ui_soweto)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = state.city,
                    onValueChange = { vm.onFieldChange("city", it) },
                    label = stringResource(R.string.ui_city),
                    modifier = Modifier.weight(1f)
                )

                Box(modifier = Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = state.province,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ui_province)) },
                        trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        SaReferenceData.PROVINCES.forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = { vm.onFieldChange("province", p); expanded = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.error != null) {
                InfoBox(state.error!!, InfoType.ERROR)
            }

            SanibonaniButton(
                text = if (state.isSubmitting) "Processing..." else "Register & Pay ${state.joiningFee.format()}",
                onClick = { clickDebouncer.processClick { vm.submit(groupId, null) } },
                enabled = state.canSubmit && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            InfoBox(
                "Your information is stored securely and only shared with group administrators for membership purposes.",
                InfoType.INFO
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}
