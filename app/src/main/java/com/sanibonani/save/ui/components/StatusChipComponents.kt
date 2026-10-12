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
import com.sanibonani.save.domain.model.AdminFeeState
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
fun PaymentChip(paid: Boolean) {
    val (bg, fg, label) = if (paid)
        Triple(SuccessBg, SuccessGreen, "Paid")
    else
        Triple(WarningBg, WarningAmber, "Due")
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            text      = label,
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AdminFeeChip(state: AdminFeeState) {
    val (bg, fg, label) = when (state) {
        AdminFeeState.PAID               -> Triple(SuccessBg, SuccessGreen,  "Fee Paid ✓")
        AdminFeeState.DUE                -> Triple(WarningBg, WarningAmber,  "Fee Due")
        AdminFeeState.OVERDUE            -> Triple(ErrorBg,   ErrorRed,      "⚠️ Overdue")
        AdminFeeState.WARNING            -> Triple(ErrorBg,   ErrorRed,      "⚠️ Warning")
        AdminFeeState.SUSPENDED          -> Triple(ErrorBg,   ErrorRed,      "🔴 Suspended")
        AdminFeeState.PENDING_ACTIVATION -> Triple(InfoBg,    InfoBlue,      "Pending Activation")
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            text      = label,
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = fg,
            fontWeight = FontWeight.Bold
        )
    }
}
