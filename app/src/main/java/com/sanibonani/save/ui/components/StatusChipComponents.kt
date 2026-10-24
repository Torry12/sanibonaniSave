package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.ui.theme.*

@Composable
fun StatusChip(status: MemberStatus, label: String? = null) {
    val (bg, fg) = when (status) {
        MemberStatus.ACTIVE          -> SuccessBg to SuccessGreen
        MemberStatus.PROBATION       -> WarningBg to WarningAmber
        MemberStatus.SUSPENDED       -> ErrorBg   to ErrorRed
        MemberStatus.PENDING_PAYMENT -> InfoBg    to InfoBlue
    }
    Surface(
        color  = bg,
        shape  = RoundedCornerShape(20.dp)
    ) {
        Text(
            text      = label ?: status.displayName,
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusChip(status: String) {
    val upperStatus = status.uppercase()
    val color = when {
        upperStatus == "PAID" || upperStatus == "COMPLETED" || upperStatus == "APPROVED" || upperStatus == "ACTIVE" -> SuccessGreen
        upperStatus == "PENDING" || upperStatus.contains("REVIEW") || upperStatus == "ESCALATED" || upperStatus == "GROUP_APPROVED" || upperStatus == "PROBATION" -> WarningAmber
        upperStatus == "FAILED" || upperStatus == "REJECTED" || upperStatus == "CANCELLED" || upperStatus == "OVERDUE" || upperStatus == "SUSPENDED" -> ErrorRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = status.replace("_", " "),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
