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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.ui.handleProfilePhotoSelection
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.member.presentation.MemberProfileViewModel

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
                        contentScale = ContentScale.Crop
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
            Text(member?.fullName ?: "N/A", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(member?.email ?: "N/A", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        GlassCard(color = MaterialTheme.colorScheme.surface) {
            SanibonaniSectionHeader(stringResource(R.string.ui_account_details))
            DetailRow("ID Number", member?.idNumber?.takeIf { it.isNotBlank() } ?: "Not captured")
            DetailRow("Phone", member?.phone ?: "Not captured")
            DetailRow("Email", member?.email ?: "Not captured")
            DetailRow("Joined", formatDate(member?.joinedAt))
            val probationEnd = member?.probationEndAt
            if (member?.status == MemberStatus.PROBATION && !probationEnd.isNullOrBlank()) {
                DetailRow("Probation Ends", formatDate(probationEnd))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Group", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupIcon(logoEmoji = group?.logoEmoji, size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(group?.name ?: "N/A", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            DetailRow("Group Type", group?.type?.displayName ?: "N/A")
            DetailRow("Member Status", member?.status?.displayName ?: "N/A")
            DetailRow("Notification Pref", member?.notificationPref?.displayName ?: "Both")
            member?.roscaPosition?.let {
                DetailRow("ROSCA Position", it.toString())
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LightGray.copy(0.3f))

            val addressParts = listOfNotNull(
                member?.street?.takeIf { it.isNotBlank() },
                member?.suburb?.takeIf { it.isNotBlank() },
                member?.city?.takeIf { it.isNotBlank() },
                member?.province?.takeIf { it.isNotBlank() }
            )
            DetailRow("Address", if (addressParts.isEmpty()) "Not captured" else addressParts.joinToString(", "))
        }

        var showBankingDialog by remember { mutableStateOf(false) }

        val surfaceColor = MaterialTheme.colorScheme.surface
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val bankColor = remember(member?.bankName, surfaceColor) {
            when (member?.bankName?.lowercase()) {
                "fnb" -> Color(0xFF005EB8)
                "capitec" -> Color(0xFFC8102E)
                "standard bank" -> Color(0xFF0033A0)
                "absa" -> Color(0xFFDA291C)
                "nedbank" -> Color(0xFF006D4E)
                else -> surfaceColor
            }
        }
        val onBankColor = if (bankColor == surfaceColor) onSurfaceColor else Color.White

        GlassCard(color = bankColor) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Banking Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = onBankColor)
                IconButton(onClick = { showBankingDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Banking Details", tint = onBankColor, modifier = Modifier.size(20.dp))
                }
            }
            DetailRow("Bank", member?.bankName?.takeIf { it.isNotBlank() } ?: "Not captured", color = onBankColor)
            DetailRow("Account No", member?.accountNo?.takeIf { it.isNotBlank() } ?: "Not captured", color = onBankColor)
            DetailRow("Branch", member?.branchCode?.takeIf { it.isNotBlank() } ?: "Not captured", color = onBankColor)
        }

        if (showBankingDialog) {
            com.sanibonani.save.features.member.ui.BankingDetailsDialog(
                initialBank = member?.bankName ?: "",
                initialAccount = member?.accountNo ?: "",
                initialBranch = member?.branchCode ?: "",
                onDismiss = { showBankingDialog = false },
                onConfirm = { b, a, br ->
                    vm.updateBankingDetails(b, a, br)
                    showBankingDialog = false
                }
            )
        }

        GlassCard(color = MaterialTheme.colorScheme.surface) {
            SanibonaniSectionHeader(stringResource(R.string.ui_security_access))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_biometric_signin), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.ui_secure_access_with_fingerprint_or_face), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(
                    checked = state.biometricEnabled,
                    onCheckedChange = { vm.toggleBiometric(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedTrackColor = LightGray
                    )
                )
            }
        }

        GlassCard(color = MaterialTheme.colorScheme.surface) {
            SanibonaniSectionHeader("Data Management")
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync All Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Refresh all group and member data from server.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    val isSyncing = state.syncStatus is SyncRepository.SyncStatus.SYNCING
                    Button(
                        onClick = { vm.syncAllData() },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Sync Now", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (state.syncStatus != SyncRepository.SyncStatus.IDLE) {
                    Spacer(Modifier.height(12.dp))
                    val statusText = when (val s = state.syncStatus) {
                        is SyncRepository.SyncStatus.SYNCING -> s.message
                        is SyncRepository.SyncStatus.ERROR -> "Sync failed: ${s.message}"
                        is SyncRepository.SyncStatus.SUCCESS -> "Sync complete"
                        else -> ""
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (state.syncStatus) {
                            is SyncRepository.SyncStatus.ERROR -> ErrorRed
                            is SyncRepository.SyncStatus.SUCCESS -> SuccessGreen
                            else -> Navy.copy(alpha = 0.7f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    if (state.syncStatus is SyncRepository.SyncStatus.SYNCING) {
                        val progress = (state.syncStatus as? SyncRepository.SyncStatus.SYNCING)?.progress ?: 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape),
                            color = Navy,
                            trackColor = LightGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        LogoutButton(
            onLogout = onLogout,
            style = if (state.isImpersonating) LogoutButtonStyle.Filled else LogoutButtonStyle.Outlined,
            label = if (state.isImpersonating) "Exit Support" else "Log Out"
        )

        Spacer(Modifier.height(40.dp))
    }
}
