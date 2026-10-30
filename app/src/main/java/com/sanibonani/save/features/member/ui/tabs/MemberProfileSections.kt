package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.sanibonani.save.ui.components.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel

@Composable
fun ProfilePhotoBox(
    member: Member?,
    profileImageVersion: Long,
    profileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    vm: MemberOverviewViewModel,
    context: android.content.Context
) {
    Box(
        modifier = Modifier.clickable { profileLauncher.launch("image/*") },
        contentAlignment = Alignment.BottomEnd
    ) {
        val url = member?.profilePhotoUrl
        if (!url.isNullOrBlank()) {
            val headers = vm.getDownloadParams(url)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .apply {
                        headers.forEach { (k, v) -> addHeader(k, v) }
                    }
                    .memoryCacheKey("$url-$profileImageVersion")
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.5.dp, Navy.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(2.5.dp, Navy.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Navy.copy(alpha = 0.3f)
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier.size(24.dp).offset(x = 4.dp, y = 4.dp),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Edit,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
fun PendingPayoutsCard(pendingClaims: List<BeneficiaryPayoutClaim>, pendingPayouts: List<PayoutRequest>, onNavigate: () -> Unit) {
    GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(16.dp)) {
            Text("Pending Payouts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            pendingClaims.forEach { com.sanibonani.save.features.member.ui.PayoutSummaryItem(claim = it) }
            pendingPayouts.forEach { com.sanibonani.save.features.member.ui.PayoutSummaryItem(payout = it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View Disbursement Hub", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Navy)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Navy, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun TrustBanner(onAuditClick: () -> Unit) {
    GlassCard(accentColor = SuccessGreen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trust & Transparency", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                Text("This group uses immutable ledgers for all transactions.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAuditClick) {
                Text("AUDIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
    }
}
