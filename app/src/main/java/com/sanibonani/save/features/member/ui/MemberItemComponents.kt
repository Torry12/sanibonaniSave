package com.sanibonani.save.features.member.ui


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.ui.utils.IDNumberTransformation
import java.math.BigDecimal
import kotlinx.coroutines.launch
@Composable
fun LoanItem(loan: Loan, onDownloadContract: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${formatZAR(loan.amount)} Loan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Requested on ${loan.createdAt?.substringBefore("T") ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!loan.contractUrl.isNullOrBlank() && (onDownloadContract != null)) {
                        IconButton(
                            onClick = onDownloadContract,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Description, "Download Agreement", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    StatusChip(status = loan.status.toMemberStatus(), label = loan.status.displayName)
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("Total Repayable", formatZAR(loan.totalToRepay), isBold = true)
                DetailRow("Remaining Balance", formatZAR(loan.balanceRemaining), isBold = true, color = MaterialTheme.colorScheme.primary)
            }

            if (loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PARTIALLY_PAID) {
                Spacer(Modifier.height(20.dp))
                val progress = if (loan.totalToRepay.isPositive) (loan.totalRepaid.ratio(loan.totalToRepay)).toFloat() else 0f

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text("Repayment Progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

fun LoanStatus.toMemberStatus(): MemberStatus = when(this) {
    LoanStatus.ACTIVE, LoanStatus.COMPLETED, LoanStatus.PARTIALLY_PAID, LoanStatus.APPROVED -> MemberStatus.ACTIVE
    LoanStatus.PENDING -> MemberStatus.PENDING_PAYMENT
    LoanStatus.REJECTED, LoanStatus.OVERDUE, LoanStatus.CANCELLED, LoanStatus.DEFAULTED -> MemberStatus.SUSPENDED
}

fun ContributionStatus.toMemberStatus(): MemberStatus = when(this) {
    ContributionStatus.PAID, ContributionStatus.PARTIAL -> MemberStatus.ACTIVE
    ContributionStatus.DUE -> MemberStatus.PENDING_PAYMENT
    ContributionStatus.OVERDUE -> MemberStatus.SUSPENDED
}

@Composable
fun NotificationItem(n: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Navy.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp), tint = Navy) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(n.message, style = MaterialTheme.typography.bodyMedium); Text(n.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun MessageItem(m: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(m.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(m.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ContributionItem(c: Contribution, onClick: (Contribution) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize().clickable { onClick(c) },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (c.type == "joining_fee") "ðŸŽŸï¸" else "ðŸ’°", fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = if (c.type == "joining_fee") "Joining Fee" else "Monthly Contribution",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(c.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatZAR(c.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                StatusChip(status = c.status.toMemberStatus(), label = c.status.name)
            }
        }
    }
}

@Composable
fun BeneficiaryItem(
    beneficiary: Beneficiary,
    isBurial: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpload: (ByteArray, String) -> Unit,
    onClaim: () -> Unit,
    onDownload: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            val bytes = context.contentResolver.openInputStream(selectedUri)?.use { s -> s.readBytes() }
            if (bytes != null) {
                onUpload(bytes, getFileNameFromUri(context.contentResolver, selectedUri))
            }
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Navy.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    val url = beneficiary.documentUrl
                    val isImage = url != null && (url.endsWith(".jpg", true) || url.endsWith(".png", true) || url.endsWith(".jpeg", true))
                    if (isImage) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Document Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, null, tint = Navy, modifier = Modifier.size(24.dp))
                    }
                }

                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = beneficiary.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isBurial) {
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(
                                text = if (beneficiary.isOver65) "Over 65" else "Standard",
                                color = if (beneficiary.isOver65) WarningAmber else SuccessGreen
                            )
                        }
                    }
                    Text(
                        text = "${beneficiary.relationship} â€¢ ${beneficiary.idNumber ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = Navy, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Navy.copy(alpha = 0.05f))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hasDoc = !beneficiary.documentUrl.isNullOrBlank()
                OutlinedButton(
                    onClick = {
                        if (hasDoc && onDownload != null) {
                            val url = beneficiary.documentUrl
                            if (url != null) onDownload(url, beneficiary.fullName)
                        } else {
                            launcher.launch("image/*")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasDoc) SuccessGreen else Navy
                    ),
                    border = BorderStroke(1.dp, (if (hasDoc) SuccessGreen else Navy).copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = if (hasDoc) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hasDoc) "View Document" else "Upload Beneficiary ID",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (hasDoc && onDownload != null) {
                    IconButton(
                        onClick = { launcher.launch("image/*") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Re-upload Document",
                            tint = Navy,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isBurial) {
                    Button(
                        onClick = onClaim,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.MedicalServices, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Initiate Claim", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            color = color
        )
    }
}

