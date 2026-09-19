package com.sanibonani.save.ui.screens.member.tabs

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.screens.member.handleProfilePhotoSelection
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.MemberProfileViewModel

@Composable
fun MemberProfileTab(member: Member?, group: Group?, vm: MemberProfileViewModel, profileImageVersion: Long) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        // handleProfilePhotoSelection(context, vm, it)
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
                if (!member?.profilePhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(member.profilePhotoUrl)
                            .memoryCacheKey("${member.profilePhotoUrl}-$profileImageVersion")
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
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(member?.fullName ?: "N/A", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(member?.email ?: "N/A", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        GlassCard(color = MaterialTheme.colorScheme.surface) {
            SectionTitle(stringResource(R.string.ui_account_details))
            DetailRow("Phone", member?.phone ?: "Not captured")
            DetailRow("Joined", member?.joinedAt?.substringBefore("T") ?: "N/A")
            DetailRow("Group", group?.name ?: "N/A")
            DetailRow("Member Status", member?.status?.displayName ?: "N/A")
        }

        GlassCard(color = MaterialTheme.colorScheme.surface) {
            SectionTitle(stringResource(R.string.ui_security_access))
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
                    onCheckedChange = { /* vm.toggleBiometric(it) */ },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedTrackColor = LightGray
                    )
                )
            }
        }

        LogoutButton(onLogout = { /* Handled by dashboard */ }, style = LogoutButtonStyle.Outlined)

        Spacer(Modifier.height(40.dp))
    }
}
