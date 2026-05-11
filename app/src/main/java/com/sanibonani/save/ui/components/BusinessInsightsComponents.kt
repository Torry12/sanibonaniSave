package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.rosca.CalculateRoscaRotationUseCase
import com.sanibonani.save.domain.usecase.investment.CalculateInvestmentClubValuationUseCase
import com.sanibonani.save.domain.usecase.stokvel.CalculateStokvelPayoutsUseCase
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.theme.MidGray
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.components.formatZAR
import com.sanibonani.save.ui.components.DetailRow

@Composable
fun RoscaInsights(schedule: CalculateRoscaRotationUseCase.RoscaSchedule) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ROSCA Rotation Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Forest)
            DetailRow("Pot Total", formatZAR(schedule.totalPot))
            DetailRow("Cycle Length", "${schedule.cycleMonths} Months")
            
            HorizontalDivider(color = Forest.copy(0.1f))
            
            schedule.items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val color = if (item.isCurrent) Forest else if (item.isCompleted) SuccessGreen else MidGray
                    val icon = if (item.isCurrent) Icons.Default.Star else if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.Circle
                    
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.memberName, fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal)
                        Text(item.payoutDate, style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                    if (item.isCurrent) {
                        Surface(color = Forest.copy(0.1f), shape = RoundedCornerShape(12.dp)) {
                            Text("CURRENT", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvestmentClubInsights(valuation: CalculateInvestmentClubValuationUseCase.PortfolioValuation) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Investment Valuation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Forest)
            DetailRow("Portfolio NAV", formatZAR(valuation.totalAssets))
            DetailRow("Unit Price", formatZAR(valuation.unitPrice))
            DetailRow("Total Units Issued", valuation.totalUnits.toInt().toString())
            
            HorizontalDivider(color = Forest.copy(0.1f))
            Text("Member Equity Breakdown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            
            valuation.memberValuations.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(m.memberName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("${m.contributionWeight.toInt()}% weight", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                    Text(formatZAR(m.marketValue), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Forest)
                }
            }
        }
    }
}

@Composable
fun StokvelInsights(projection: CalculateStokvelPayoutsUseCase.PayoutProjection) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Annual Payout Projection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Forest)
            DetailRow("Months to Year-End", "${projection.monthsRemaining} Months")
            DetailRow("Projected Total Fund", formatZAR(projection.totalProjectedFund))
            
            HorizontalDivider(color = Forest.copy(0.1f))
            
            projection.memberProjections.forEach { m ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.memberName, style = MaterialTheme.typography.bodySmall)
                        Text(formatZAR(m.projectedFinalPayout), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    val progress = if (m.projectedFinalPayout > 0) (m.currentSavings / m.projectedFinalPayout).toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(CircleShape),
                        color = Forest,
                        trackColor = Forest.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}
