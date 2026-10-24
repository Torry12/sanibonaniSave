package com.sanibonani.save.features.admin.ui.components

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.GroupFinancialInsight
import com.sanibonani.save.ui.theme.SuccessGreen

@Composable
fun FullInsightWidget(insight: GroupFinancialInsight) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ui_detailed_risk_analysis), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Risk: ${insight.riskLevel.name}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(insight.statusSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))

            if (insight.keyFindings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Key Findings:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                insight.keyFindings.forEach { finding ->
                    Text("• $finding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (insight.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Recommendations:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                insight.recommendations.forEach { rec ->
                    Text("✓ $rec", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                }
            }
        }
    }
}
