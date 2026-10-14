package com.sanibonani.save.features.member.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.features.admin.ui.components.maskAccountNumberForDisplay
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.system.SyncStatus
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.ui.handleProfilePhotoSelection
import com.sanibonani.save.features.member.ui.BankingDetailsDialog
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.member.presentation.MemberProfileViewModel
import com.sanibonani.save.ui.components.LogoutButtonStyle
import com.sanibonani.save.ui.utils.formatZAR

@Composable
fun MemberProfileTab(
    member: Member?,
    group: Group?,
    vm: MemberProfileViewModel,
    profileImageVersion: Long,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        handleProfilePhotoSelection(context, vm, it)
    }

    var showBankEdit by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = BorderStroke(2.dp, Navy.copy(alpha = 0.1f))
            ) {
                val url = member?.profilePhotoUrl
                if (!url.isNullOrBlank()) {
                    val headers = vm.getDownloadParams(url)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .apply {
                                headers.forEach { entry -> addHeader(entry.key, entry.value) }
                            }
                            .memoryCacheKey("$url-$profileImageVersion")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(56.dp), tint = Navy.copy(0.3f))
                    }
                }
            }
            Surface(
                onClick = { launcher.launch("image/*") },
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(36.dp).offset(x = 8.dp, y = 8.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Edit,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(member?.fullName ?: "Loading...", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(member?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MidGray)
        }

        GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
            SanibonaniSectionHeader(stringResource(R.string.ui_account_details), modifier = Modifier.padding(top = 0.dp))
            DetailRow("Group", group?.name ?: "N/A")
            DetailRow("Joined", formatDate(member?.joinedAt))
            DetailRow("Status", member?.status?.displayName ?: "N/A", isBold = true, color = if (member?.status == MemberStatus.SUSPENDED) ErrorRed else MaterialTheme.colorScheme.primary)
            DetailRow("Total Paid", formatZAR(member?.totalPaid ?: Money.ZERO), isBold = true)
        }

        GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                SanibonaniSectionHeader("Banking", modifier = Modifier.padding(top = 0.dp))
                TextButton(
                    onClick = { showBankEdit = true },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Edit", fontWeight = FontWeight.Bold)
                }
            }
            DetailRow("Bank", member?.bankName ?: "Not set")
            DetailRow("Account", member?.accountNo?.let { maskAccountNumberForDisplay(it) } ?: "Not set")
            DetailRow("Branch", member?.branchCode ?: "Not set")
        }

        GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
            SanibonaniSectionHeader(stringResource(R.string.ui_language), modifier = Modifier.padding(top = 0.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(stringResource(R.string.ui_select_language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                Spacer(Modifier.height(12.dp))
                LanguageSelector()
            }
        }

        GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
            SanibonaniSectionHeader(stringResource(R.string.ui_security_access), modifier = Modifier.padding(top = 0.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_biometric_signin), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.ui_secure_access_with_fingerprint_or_face), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
                Switch(
                    checked = state.biometricEnabled,
                    onCheckedChange = { vm.toggleBiometric(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }

        val syncStatus = state.syncStatus
        if (syncStatus != SyncStatus.Idle) {
            GlassCard(accentColor = Forest) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val statusText = when (syncStatus) {
                        is SyncStatus.Progress -> syncStatus.message
                        is SyncStatus.Error -> "Sync failed: ${syncStatus.message}"
                        is SyncStatus.Completed -> "Sync complete"
                        else -> ""
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (syncStatus) {
                            is SyncStatus.Error -> ErrorRed
                            is SyncStatus.Completed -> SuccessGreen
                            else -> Navy.copy(alpha = 0.7f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    if (syncStatus is SyncStatus.Progress) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { syncStatus.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = Navy,
                            trackColor = LightGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        var showUserManual by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { showUserManual = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy),
            border = BorderStroke(1.dp, Navy.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("User Manual & Help")
        }

        if (showUserManual) {
            UserManualDialog(onDismiss = { showUserManual = false })
        }

        LogoutButton(
            onLogout = onLogout,
            style = if (state.isImpersonating) LogoutButtonStyle.Filled else LogoutButtonStyle.Outlined
        )

        if (showBankEdit) {
            BankingDetailsDialog(
                initialBank = member?.bankName ?: "",
                initialAccount = member?.accountNo ?: "",
                initialBranch = member?.branchCode ?: "",
                onDismiss = { showBankEdit = false },
                onConfirm = { b: String, a: String, br: String ->
                    vm.updateBankingDetails(b, a, br)
                    showBankEdit = false
                }
            )
        }
    }
}

