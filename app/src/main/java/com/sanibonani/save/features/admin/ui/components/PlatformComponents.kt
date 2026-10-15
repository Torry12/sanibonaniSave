package com.sanibonani.save.features.admin.ui.components

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.components.AutoFitText
import com.sanibonani.save.ui.utils.*

/**
 * Masks account number for security display in payout cards.
 * Shows only last 4 digits.
 * Example: "1234567890" → "****7890"
 */
fun maskAccountNumberForDisplay(accountNumber: String): String = when {
    accountNumber.length <= 4 -> accountNumber
    else -> "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
}

@Composable
fun AccordionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    label: String = "",
    icon: ImageVector? = null,
    isSubHeader: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isSubHeader) 8.dp else 12.dp,
                vertical = if (isSubHeader) 8.dp else 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isSubHeader) 20.dp else 24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = if (isSubHeader) title.uppercase() else title,
                style = if (isSubHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                color = if (isSubHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                letterSpacing = if (isSubHeader) 1.sp else 0.sp
            )

            if (count > 0) {
                Text(
                    text = if (label.isEmpty()) count.toString() else "$count $label",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSubHeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isSubHeader) 20.dp else 24.dp)
            )
        }
    }
}

@Composable
fun ProvinceHeaderCard(
    province: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    label: String = "groups"
) {
    AccordionHeader(
        title = province,
        count = count,
        isExpanded = isExpanded,
        onClick = onClick,
        label = label,
        isSubHeader = true
    )
}

@Composable
fun BehaviorInsightCard(insight: MemberBehaviorInsight, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AutoFitText(
                    text = insight.memberName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            DetailRow("Risk Band", insight.riskBand, isBold = true)
            DetailRow("Overdue Loans", insight.overdueLoans.toString(), isBold = true, color = if (insight.overdueLoans > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            DetailRow("Behavior Score", "${insight.behaviorScore.toInt()}/100", isBold = true, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyStateSmall(message: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface, // Use full onSurface for visibility
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DistributionRow(label: String, count: Int, pct: Float, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AutoFitText(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                minFontSize = 10.sp
            )
            Spacer(Modifier.width(8.dp))
            Text("$count (${(pct * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun MetricRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun HeaderWithIcon(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.5.sp
        )
    }
}
