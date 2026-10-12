package com.sanibonani.save.features.admin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

import com.sanibonani.save.ui.components.ActionButton

@Composable
fun AdminFeeBanner(group: Group, status: AdminFeeState, daysOverdue: Int, onAction: () -> Unit) {
    if (status == AdminFeeState.PAID) return

    val (bgColor, fgColor) = when (status) {
        AdminFeeState.DUE -> InfoBg to InfoBlue
        AdminFeeState.WARNING -> WarningBg to WarningAmber
        AdminFeeState.OVERDUE, AdminFeeState.SUSPENDED -> ErrorBg to ErrorRed
        AdminFeeState.PENDING_ACTIVATION -> InfoBg to InfoBlue
        AdminFeeState.PAID -> SuccessBg to SuccessGreen
    }

    val message = when (status) {
        AdminFeeState.DUE -> "Platform fee is due."
        AdminFeeState.WARNING -> "Platform fee is late. Pay soon to avoid suspension."
        AdminFeeState.OVERDUE -> "Group suspended due to non-payment ($daysOverdue days late)."
        AdminFeeState.SUSPENDED -> "Account restricted. Please settle outstanding fees."
        AdminFeeState.PENDING_ACTIVATION -> "Group activation required."
        else -> ""
    }

    // High-contrast text color for light backgrounds in both light and dark mode
    val textColor = Color.Black.copy(alpha = 0.8f)

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = fgColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(message, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = textColor)
                Text(
                    text = stringResource(R.string.ui_settle_your_account_to_keep_your_group_a),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onAction) {
                Text(
                    text = if (status == AdminFeeState.PENDING_ACTIVATION) "ACTIVATE" else stringResource(R.string.ui_pay_now),
                    color = fgColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
