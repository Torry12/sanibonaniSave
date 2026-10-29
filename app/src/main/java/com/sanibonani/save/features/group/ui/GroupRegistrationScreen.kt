package com.sanibonani.save.features.group.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniTopBar
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.features.financial.presentation.PaymentViewModel
import com.sanibonani.save.features.financial.ui.PaymentScreen
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.steps.RegStep1
import com.sanibonani.save.features.group.ui.steps.RegStep2
import com.sanibonani.save.features.group.ui.steps.RegStep3
import com.sanibonani.save.features.group.ui.steps.RegStep4
import com.sanibonani.save.features.group.ui.steps.RegStep5
import com.sanibonani.save.features.group.ui.steps.RegStepConstitution
import com.sanibonani.save.ui.components.AnimatedStepTransition
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.SanibonaniLoadingScreen
import com.sanibonani.save.ui.components.SoftLoadingOverlay
import com.sanibonani.save.ui.utils.rememberClickDebouncer

/**
 * Six-step group registration wizard.
 *
 * This composable is the shell only: it owns the scaffold, step progress, the
 * payment hand-off and navigation. Each step lives in
 * `features.group.ui.steps.*`, and shared widgets in
 * `features.group.ui.components.*`.
 */
@Composable
fun GroupRegistrationScreen(
    onSuccess: (String) -> Unit,
    onBack: () -> Unit,
    initialGroupType: GroupType? = null,
    vm: GroupRegistrationViewModel = hiltViewModel<GroupRegistrationViewModel>()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()
    val payVm: PaymentViewModel = hiltViewModel()

    // Pre-select the group type chosen on the onboarding screen.
    LaunchedEffect(initialGroupType) {
        if (initialGroupType != null && state.type != initialGroupType) {
            vm.onEvent(GroupFormEvent.TypeSelected(initialGroupType))
        }
    }

    LaunchedEffect(state.success, state.createdGroupId) {
        if (state.success) {
            val gid = state.createdGroupId
            if (gid != null) {
                Toast.makeText(context, "Group registered successfully!", Toast.LENGTH_LONG).show()
                // Keep the success state on screen: the caller re-resolves the creator's role
                // before navigating, and resetting here would flash the empty form while waiting.
                onSuccess(gid)
            }
        }
    }

    if (state.needsPayment) {
        val payState by payVm.state.collectAsStateWithLifecycle()

        PaymentScreen(
            paymentType = "registration",
            amount = state.platformRegistrationFee.takeIf { it.isPositive } ?: PlatformFees.REGISTRATION,
            groupId = state.createdGroupId ?: "new_group",
            onPaymentComplete = {
                vm.finalizeRegistrationAfterPayment(payState.transactionId)
            },
            onPaymentFailed = { e ->
                com.sanibonani.save.domain.logging.AppLogger.w(
                    "GroupRegistration",
                    "Platform registration payment failed: ${e.message}"
                )
            },
            onBack = {
                payVm.clearTransactionState()
                vm.onEvent(GroupFormEvent.DismissPayment)
            },
            vm = payVm
        )
    } else if (state.isSubmitting && !state.success) {
        SanibonaniLoadingScreen(message = stringResource(R.string.ui_creating_group))
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
                        },
                        actions = {
                            if (!state.success) {
                                var showCancelDialog by remember { mutableStateOf(false) }
                                TextButton(onClick = { showCancelDialog = true }) {
                                    Text(
                                        "Cancel",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (showCancelDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showCancelDialog = false },
                                        title = { Text(stringResource(R.string.ui_cancel_group_registration)) },
                                        text = { Text(stringResource(R.string.ui_cancel_registration_warning)) },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    showCancelDialog = false
                                                    vm.resetNavigation()
                                                    onBack()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text(stringResource(R.string.ui_yes_cancel))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showCancelDialog = false }) {
                                                Text(stringResource(R.string.ui_resume))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                    SoftLoadingOverlay(
                        isLoading = state.isLoading,
                        message = stringResource(R.string.ui_updating_fee_configurations)
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                if (state.success) {
                    RegistrationSuccessContent()
                } else {
                    val progress by remember { derivedStateOf { state.currentStep / state.totalSteps.toFloat() } }
                    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "Step ${state.currentStep}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${(progress * 100).toInt()}% Complete",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            AnimatedStepTransition(currentStep = state.currentStep) { step ->
                                when (step) {
                                    1 -> RegStep1(state, vm)
                                    2 -> RegStep2(state, vm)
                                    3 -> RegStep3(state, vm)
                                    4 -> RegStep4(state, vm)
                                    5 -> RegStepConstitution(state, vm)
                                    6 -> RegStep5(state, vm)
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = state.error != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        state.error?.let { error ->
                            InfoBox(error, type = InfoType.ERROR)
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (state.currentStep > 1) {
                            OutlinedButton(
                                onClick = { clickDebouncer.processClick { vm.prevStep() } },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    stringResource(R.string.ui_back),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        SanibonaniButton(
                            text = state.summaryActionLabel,
                            onClick = {
                                clickDebouncer.processClick {
                                    if (state.currentStep == 6) vm.submitGroup() else vm.nextStep()
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            isLoading = state.isSubmitting
                        )
                    }
                }
            }
        }
    }
}

/** Shown while the success redirect fires. */
@Composable
private fun RegistrationSuccessContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.ui_group_created),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.ui_you_will_be_redirected_shortly),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
