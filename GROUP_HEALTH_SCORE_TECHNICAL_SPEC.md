# Group Health Score: Technical Specification & Implementation Guide
**Feature**: Actuarial-based group viability assessment with dashboard visualization

**Status**: Ready for Implementation  
**Priority**: P0 (Core differentiator)  
**Timeline**: 2-3 weeks (sprints 1-2)  
**Owners**: Android Lead, Actuarial Consultant (optional)

---

## 1. Requirements & User Stories

### Primary User Story
```gherkin
Feature: Group Health Score Dashboard
  As an admin
  I want to see my group's health score on the dashboard
  So that I can understand viability risks and take corrective action

  Scenario: Admin views group health score
    Given I am logged in as a group admin
    And my group has been active for 3+ months
    When I open the admin dashboard
    Then I should see a "Group Health Score" card
    And the card displays a score (0-100)
    And the card is color-coded (red/yellow/green)
    And I can click "Details" to see component breakdown
    And I can click "Generate Report" to download PDF
```

### Secondary User Stories
1. Admin exports health score report as PDF
2. Member views group health (read-only, on member portal)
3. Platform admin monitors all groups' health scores (analytics dashboard)
4. System sends automated alerts when group enters Red Zone

---

## 2. Domain Model & Data Structures

### Core Data Models

```kotlin
// New file: domain/model/GroupHealthScore.kt

package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

enum class RiskZone {
    RED,     // < 40 points: Critical risk
    YELLOW,  // 40-70 points: Moderate risk
    GREEN    // > 70 points: Healthy
}

@Serializable
data class GroupHealthScore(
    val groupId: String,
    val overallScore: Int,                          // 0-100
    val zone: RiskZone,
    val components: Map<String, Int>,               // Component name → score
    val recommendations: List<String> = emptyList(),
    val generatedAt: String,
    val expiresAt: String?  = null                  // Cache expiration
) : JavaSerializable

@Serializable
data class HealthScoreComponent(
    val name: String,                // e.g., "Solvency Ratio"
    val score: Int,                  // Component's contribution to total
    val weight: Float,               // e.g., 0.25 = 25% of total score
    val formula: String,             // "Assets / Liabilities"
    val currentValue: Double,        // Actual calculated value
    val benchmark: Double,           // Industry benchmark
    val status: String               // "Good", "Warning", "Critical"
)

@Serializable
data class HealthScoreRecommendation(
    val id: String,
    val groupId: String,
    val title: String,               // e.g., "Increase monthly contribution"
    val description: String,
    val priority: RecommendationPriority,
    val category: String,            // "Contributions", "Retention", "Claims"
    val estimatedImpact: String,     // e.g., "Would move to Green zone"
    val createdAt: String
)

enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW
}
```

### Database Entities

```kotlin
// New file: data/local/entity/HealthScoreEntity.kt

package com.sanibonani.save.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable

@Entity(
    tableName = "group_health_scores",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("group_id"),
        Index("generated_at")
    ]
)
@Serializable
data class GroupHealthScoreEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("group_id") val groupId: String,
    @ColumnInfo("overall_score") val overallScore: Int,
    @ColumnInfo("zone") val zone: String,  // RED, YELLOW, GREEN
    @ColumnInfo("components_json") val componentsJson: String,  // JSON map
    @ColumnInfo("recommendations_json") val recommendationsJson: String,  // JSON list
    @ColumnInfo("generated_at") val generatedAt: String,
    @ColumnInfo("expires_at") val expiresAt: String?
) {
    fun toModel(): GroupHealthScore {
        val components = Json.decodeFromString<Map<String, Int>>(componentsJson)
        val recs = Json.decodeFromString<List<String>>(recommendationsJson)
        return GroupHealthScore(
            groupId = groupId,
            overallScore = overallScore,
            zone = RiskZone.valueOf(zone),
            components = components,
            recommendations = recs,
            generatedAt = generatedAt,
            expiresAt = expiresAt
        )
    }
}

@Serializable
data class RecommendationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("group_id") val groupId: String,
    val title: String,
    val description: String,
    val priority: String,  // HIGH, MEDIUM, LOW
    val category: String,
    val estimatedImpact: String,
    @ColumnInfo("created_at") val createdAt: String,
    @ColumnInfo("dismissed_at") val dismissedAt: String?  // Can dismiss recommendations
)
```

---

## 3. Algorithm: Health Score Calculation

### Scoring Framework

The health score is a **weighted composite** of 5 actuarial metrics:

| Component | Weight | Calculation | Range | Green (>70) | Yellow (40-70) | Red (<40) |
|-----------|--------|---|---|---|---|---|
| **Solvency Ratio** | 25% | Balance / Monthly Avg Contributions | 0-200% | >100% | 50-100% | <50% |
| **Loss Ratio** | 25% | Total Claims / Total Contributions | 0-100% | <40% | 40-70% | >70% |
| **Reserve Adequacy** | 20% | Balance / (Avg Monthly × 6) | 0-120% | >100% | 50-100% | <50% |
| **Funding Ratio** | 20% | Current Assets / Liability APV | 0-200% | >120% | 80-120% | <80% |
| **Member Retention** | 10% | Current Members / Prev Month Members | 0-100% | >95% | 80-95% | <80% |

### Scoring Function

```kotlin
// New file: domain/usecase/CalculateGroupHealthScoreUseCase.kt

package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class CalculateGroupHealthScoreUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
    private val contributionRepository: ContributionRepository
) {
    /**
     * Calculate composite health score (0-100) for a group.
     * 
     * Score = 25% * solvency + 25% * loss_ratio + 20% * reserve + 20% * funding + 10% * retention
     * 
     * All components normalized to 0-100 scale before weighted averaging.
     */
    suspend operator fun invoke(groupId: String): Result<GroupHealthScore> = runCatching {
        val group = groupRepository.getGroup(groupId).getOrThrow()
        val members = memberRepository.getMembers(groupId).getOrThrow()
        val contributions = contributionRepository.getContributions(groupId).getOrThrow()
        val previousMonthMembers = memberRepository.getPreviousMonthMemberCount(groupId).getOrThrow()
        
        // Calculate raw metrics
        val solvencyRatio = calculateSolvencyRatio(group, contributions)
        val lossRatio = calculateLossRatio(group, contributions)
        val reserveAdequacy = calculateReserveAdequacy(group, contributions)
        val fundingRatio = calculateFundingRatio(group)
        val memberRetention = calculateMemberRetention(members.size, previousMonthMembers)
        
        // Normalize to 0-100 scale
        val solvencyScore = normalizeSolvencyToScore(solvencyRatio)       // 0-25 points
        val lossScore = normalizeLossRatioToScore(lossRatio)             // 0-25 points
        val reserveScore = normalizeReserveAdequacyToScore(reserveAdequacy) // 0-20 points
        val fundingScore = normalizeFundingRatioToScore(fundingRatio)    // 0-20 points
        val retentionScore = normalizeRetentionToScore(memberRetention) // 0-10 points
        
        // Calculate weighted composite
        val totalScore = (solvencyScore + lossScore + reserveScore + fundingScore + retentionScore).toInt()
        
        // Determine risk zone
        val zone = when {
            totalScore < 40 -> RiskZone.RED
            totalScore < 70 -> RiskZone.YELLOW
            else -> RiskZone.GREEN
        }
        
        // Generate recommendations based on weak components
        val recommendations = generateRecommendations(
            solvencyRatio, lossRatio, reserveAdequacy, fundingRatio, memberRetention, zone
        )
        
        // Create timestamp
        val now = kotlinx.datetime.Clock.System.now().toString()
        val expiresAt = (kotlinx.datetime.Clock.System.now() + 7.days).toString()  // Cache for 7 days
        
        GroupHealthScore(
            groupId = groupId,
            overallScore = totalScore,
            zone = zone,
            components = mapOf(
                "Solvency Ratio" to solvencyScore.toInt(),
                "Loss Ratio" to lossScore.toInt(),
                "Reserve Adequacy" to reserveScore.toInt(),
                "Funding Ratio" to fundingScore.toInt(),
                "Member Retention" to retentionScore.toInt()
            ),
            recommendations = recommendations,
            generatedAt = now,
            expiresAt = expiresAt
        )
    }
    
    // ===== Component Calculations =====
    
    private fun calculateSolvencyRatio(group: Group, contributions: List<Contribution>): Double {
        val avgMonthlyContribution = contributions
            .groupBy { it.dueDate?.substringBefore("-") }  // Group by month
            .values
            .map { it.sumOf { c -> c.amount } }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        return if (avgMonthlyContribution > 0) group.balance / avgMonthlyContribution else 0.0
    }
    
    private fun calculateLossRatio(group: Group, contributions: List<Contribution>): Double {
        val totalClaims = group.payoutsProcessed.sumOf { it.amount }
        val totalContributions = contributions.sumOf { it.amount }
        
        return if (totalContributions > 0) totalClaims / totalContributions else 0.0
    }
    
    private fun calculateReserveAdequacy(group: Group, contributions: List<Contribution>): Double {
        val avgMonthlyContribution = contributions
            .groupBy { it.dueDate?.substringBefore("-") }
            .values
            .map { it.sumOf { c -> c.amount } }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        val sixMonthsExpected = avgMonthlyContribution * 6
        return if (sixMonthsExpected > 0) group.balance / sixMonthsExpected else 0.0
    }
    
    private fun calculateFundingRatio(group: Group): Double {
        val apv = calculateActuarialPresentValue(group)  // Liability
        return if (apv > 0) group.balance / apv else 0.0
    }
    
    private fun calculateMemberRetention(currentMembers: Int, previousMonthMembers: Int): Double {
        return if (previousMonthMembers > 0) currentMembers.toDouble() / previousMonthMembers else 1.0
    }
    
    private fun calculateActuarialPresentValue(group: Group): Double {
        // Simplified: Expected claims over 12 months, discounted at 5% annually
        val avgMonthlyPayout = 0.0  // TODO: Calculate from historical payouts
        val discountRate = 0.05 / 12  // Monthly
        var apv = 0.0
        for (month in 1..12) {
            apv += avgMonthlyPayout / (1 + discountRate).pow(month)
        }
        return apv
    }
    
    // ===== Normalization Functions (Convert Raw Metrics to 0-100 Scale) =====
    
    private fun normalizeSolvencyToScore(ratio: Double): Int {
        // Ideal is 1.0 (100%). Score peaks at 1.5 (150%).
        // <0.5: 0 pts, 0.5-1.5: linear scale, >1.5: 25 pts
        return when {
            ratio < 0.5 -> 0
            ratio > 1.5 -> 25
            else -> ((ratio - 0.5) / 1.0 * 25).toInt()
        }
    }
    
    private fun normalizeLossRatioToScore(ratio: Double): Int {
        // Ideal is 0% (no claims). Problematic above 70%.
        // >0.7: 0 pts, 0-0.7: inverse scale, 0%: 25 pts
        return when {
            ratio > 0.7 -> 0
            ratio < 0.0 -> 25
            else -> ((1 - ratio / 0.7) * 25).toInt()
        }
    }
    
    private fun normalizeReserveAdequacyToScore(ratio: Double): Int {
        // Ideal is 1.0 (one year of contributions). Problematic below 25%.
        // <0.25: 0 pts, 0.25-1.5: scale, >1.5: 20 pts
        return when {
            ratio < 0.25 -> 0
            ratio > 1.5 -> 20
            else -> ((ratio - 0.25) / 1.25 * 20).toInt()
        }
    }
    
    private fun normalizeFundingRatioToScore(ratio: Double): Int {
        // Ideal is 1.2 (120% funded). Problematic below 80%.
        return when {
            ratio < 0.8 -> 0
            ratio > 1.5 -> 20
            else -> ((ratio - 0.8) / 0.7 * 20).toInt()
        }
    }
    
    private fun normalizeRetentionToScore(retention: Double): Int {
        // Ideal > 95%. Problematic below 80%.
        return when {
            retention < 0.8 -> 0
            retention > 0.95 -> 10
            else -> ((retention - 0.8) / 0.15 * 10).toInt()
        }
    }
    
    // ===== Recommendation Generation =====
    
    private fun generateRecommendations(
        solvency: Double,
        lossRatio: Double,
        reserve: Double,
        funding: Double,
        retention: Double,
        zone: RiskZone
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (solvency < 0.7) {
            recommendations.add("🔴 Increase monthly contribution to build reserves (currently ${(solvency * 100).toInt()}% of target)")
        }
        if (lossRatio > 0.5) {
            recommendations.add("🔴 Claims are ${(lossRatio * 100).toInt()}% of contributions. Consider eligibility restrictions.")
        }
        if (reserve < 0.5) {
            recommendations.add("🟡 Reserves are low. Suspend payouts until balance reaches 6 months.")
        }
        if (retention < 0.9) {
            recommendations.add("🟡 Member retention is ${(retention * 100).toInt()}%. Investigate dropoff reasons.")
        }
        if (funding < 1.0) {
            recommendations.add("🟡 Group is underfunded. Plan for contribution increases.")
        }
        if (zone == RiskZone.GREEN) {
            recommendations.add("✅ Your group is in excellent standing. Consider expanding offerings (e.g., loans).")
        }
        
        return recommendations.take(5)  // Limit to 5 recommendations
    }
}
```

---

## 4. UI Components

### 4.1 Health Score Dashboard Card (Admin Dashboard)

```kotlin
// New file: ui/components/GroupHealthScoreCard.kt

package com.sanibonani.save.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.model.RiskZone

@Composable
fun GroupHealthScoreCard(
    score: GroupHealthScore?,
    isLoading: Boolean = false,
    onDetailsClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .marginBottom(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Group Health Score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Health score info",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                )
            } else if (score != null) {
                // Score circle with zone coloring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(
                            color = score.zone.backgroundColor(),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = score.overallScore.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Zone label
                Surface(
                    color = score.zone.backgroundColor().copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = score.zone.label(),
                        modifier = Modifier.padding(8.dp, 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = score.zone.backgroundColor(),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Top recommendations
                if (score.recommendations.isNotEmpty()) {
                    Text(
                        "Recommendations",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.marginBottom(8.dp)
                    )
                    score.recommendations.take(3).forEach { rec ->
                        Text(
                            text = "• $rec",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .marginBottom(6.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .marginTop(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDetailsClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Details")
                    }
                    Button(
                        onClick = onReportClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Report")
                    }
                }
            }
        }
    }
}

// Extension functions for RiskZone
fun RiskZone.backgroundColor(): Color = when (this) {
    RiskZone.RED -> Color(0xFFE53935)
    RiskZone.YELLOW -> Color(0xFFFFA726)
    RiskZone.GREEN -> Color(0xFF66BB6A)
}

fun RiskZone.label(): String = when (this) {
    RiskZone.RED -> "At Risk (Red Zone)"
    RiskZone.YELLOW -> "Caution (Yellow Zone)"
    RiskZone.GREEN -> "Healthy (Green Zone)"
}
```

### 4.2 Health Score Details Screen

```kotlin
// New file: ui/screens/admin/HealthScoreDetailScreen.kt

@Composable
fun HealthScoreDetailScreen(
    score: GroupHealthScore,
    onExportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // Overall score section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Overall Score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = (score.overallScore / 100f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = score.zone.backgroundColor()
                )
                Text(
                    "${score.overallScore}/100 - ${score.zone.label()}",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Component Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Component cards
        items(score.components.size) { index ->
            val (name, value) = score.components.toList()[index]
            ComponentScoreCard(
                name = name,
                score = value,
                weight = getComponentWeight(name)
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Recommendations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(score.recommendations.size) { index ->
            RecommendationCard(text = score.recommendations[index])
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onExportReport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Full Report as PDF")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ComponentScoreCard(
    name: String,
    score: Int,
    weight: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "$score/100",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = (score / 100f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Text(
                "Weight: ${(weight * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getComponentWeight(name: String): Float = when (name) {
    "Solvency Ratio" -> 0.25f
    "Loss Ratio" -> 0.25f
    "Reserve Adequacy" -> 0.20f
    "Funding Ratio" -> 0.20f
    "Member Retention" -> 0.10f
    else -> 0f
}
```

---

## 5. Repository Implementation

```kotlin
// New file: data/repository/HealthScoreRepositoryImpl.kt

package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.repository.HealthScoreRepository
import com.sanibonani.save.data.local.SanibonaniDatabase
import io.github.postgrest.utils.parseAsClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HealthScoreRepositoryImpl @Inject constructor(
    private val db: SanibonaniDatabase,
    private val supabase: SupabaseClient
) : HealthScoreRepository {
    
    override fun observeGroupHealthScore(groupId: String): Flow<Result<GroupHealthScore>> =
        observeAndSync(
            dbFlow = db.healthScoreDao().observeByGroup(groupId).map { list ->
                list.firstOrNull()?.toModel()
            },
            mapper = { it },
            toEntity = { it.toEntity() },
            networkFetch = {
                supabase.postgrest["group_health_scores"]
                    .select { filter { "group_id" eq groupId } }
                    .decodeSingle<GroupHealthScoreEntity>()
                    .toModel()
            },
            cacheSync = { score ->
                db.healthScoreDao().insert(score.toEntity())
            }
        )
    
    override suspend fun saveHealthScore(score: GroupHealthScore): Result<Unit> = runCatching {
        val entity = score.toEntity()
        
        // Remote
        supabase.postgrest["group_health_scores"].upsert(entity) { 
            select()
        }
        
        // Local
        db.healthScoreDao().insert(entity)
    }
    
    override suspend fun getHealthScore(groupId: String): Result<GroupHealthScore> = runCatching {
        val cached = db.healthScoreDao().getByGroup(groupId)?.toModel()
        if (cached != null && cached.expiresAt?.let { it > Clock.System.now().toString() } == true) {
            return@runCatching cached
        }
        
        // Fetch fresh
        supabase.postgrest["group_health_scores"]
            .select { filter { "group_id" eq groupId } }
            .decodeSingle<GroupHealthScoreEntity>()
            .toModel()
    }
}

// Extension functions for conversion
fun GroupHealthScore.toEntity(): GroupHealthScoreEntity = GroupHealthScoreEntity(
    id = UUID.randomUUID().toString(),
    groupId = groupId,
    overallScore = overallScore,
    zone = zone.name,
    componentsJson = Json.encodeToString(components),
    recommendationsJson = Json.encodeToString(recommendations),
    generatedAt = generatedAt,
    expiresAt = expiresAt
)
```

---

## 6. ViewModel Integration

```kotlin
// Update existing AdminViewModel

@HiltViewModel
class AdminViewModel @Inject constructor(
    // ...existing injections...
    private val calculateHealthScoreUseCase: CalculateGroupHealthScoreUseCase,
    private val healthScoreRepository: HealthScoreRepository
) : ViewModel() {
    
    private val _healthScore = MutableStateFlow<GroupHealthScore?>(null)
    val healthScore: StateFlow<GroupHealthScore?> = _healthScore.asStateFlow()
    
    private val _healthScoreLoading = MutableStateFlow(false)
    val healthScoreLoading: StateFlow<Boolean> = _healthScoreLoading.asStateFlow()
    
    fun calculateGroupHealthScore() {
        viewModelScope.launch {
            _healthScoreLoading.update { true }
            calculateHealthScoreUseCase(currentGroupId)
                .onSuccess { score ->
                    _healthScore.update { score }
                    healthScoreRepository.saveHealthScore(score)
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
            _healthScoreLoading.update { false }
        }
    }
    
    fun exportHealthScorePdf(): Result<Uri> {
        val score = _healthScore.value ?: return Result.failure(Exception("No health score available"))
        val group = _state.value.group ?: return Result.failure(Exception("No group data"))
        
        return HealthScorePdfGenerator(context).generatePdf(score, group)
            .map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            }
    }
}
```

---

## 7. Database Schema Update

```sql
-- Run in Supabase SQL editor
-- Add tables for health scores and recommendations

CREATE TABLE group_health_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL UNIQUE REFERENCES groups(id) ON DELETE CASCADE,
    overall_score INT NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    zone TEXT NOT NULL CHECK (zone IN ('RED', 'YELLOW', 'GREEN')),
    components_json JSONB NOT NULL,  -- {solvency_ratio: 20, loss_ratio: 25, ...}
    recommendations_json JSONB NOT NULL,  -- ["Increase contribution", ...]
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_group_health_scores_generated_at ON group_health_scores(generated_at DESC);
CREATE INDEX idx_group_health_scores_zone ON group_health_scores(zone);

-- Add RLS policies
ALTER TABLE group_health_scores ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Members can view group health score" ON group_health_scores
    FOR SELECT
    USING (
        group_id IN (
            SELECT id FROM groups WHERE id IN (
                SELECT group_id FROM members WHERE user_id = auth.uid()
            )
        )
    );

CREATE POLICY "Admins can view/update group health score" ON group_health_scores
    FOR ALL
    USING (
        group_id IN (
            SELECT id FROM groups WHERE admin_id = auth.uid()
        )
    );
```

---

## 8. Testing Strategy

### Unit Tests

```kotlin
// New file: domain/usecase/CalculateGroupHealthScoreUseCaseTest.kt

class CalculateGroupHealthScoreUseCaseTest {
    
    private lateinit var useCase: CalculateGroupHealthScoreUseCase
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val paymentRepository: PaymentRepository = mockk()
    private val contributionRepository: ContributionRepository = mockk()
    
    @Before
    fun setUp() {
        useCase = CalculateGroupHealthScoreUseCase(
            groupRepository, memberRepository, paymentRepository, contributionRepository
        )
    }
    
    @Test
    fun `returns RED zone for group with low solvency`() = runBlocking {
        // Given
        val group = Group(id = "g1", balance = 100.0)
        val contributions = listOf(
            Contribution(amount = 1000.0, dueDate = "2026-04-01"),
            Contribution(amount = 1000.0, dueDate = "2026-03-01")
        )
        
        coEvery { groupRepository.getGroup("g1") } returns Result.success(group)
        coEvery { memberRepository.getMembers("g1") } returns Result.success(listOf())
        coEvery { contributionRepository.getContributions("g1") } returns Result.success(contributions)
        coEvery { memberRepository.getPreviousMonthMemberCount("g1") } returns Result.success(5)
        
        // When
        val result = useCase("g1")
        
        // Then
        assert(result.isSuccess)
        val score = result.getOrThrow()
        assert(score.zone == RiskZone.RED)
        assert(score.overallScore < 40)
    }
    
    @Test
    fun `returns GREEN zone for healthy group`() = runBlocking {
        // Given
        val group = Group(id = "g1", balance = 12000.0)  // 12 months of contributions
        val contributions = (1..12).map {
            Contribution(amount = 1000.0, dueDate = "2026-${it:02d}-01")
        }
        
        // Setup mocks
        coEvery { groupRepository.getGroup("g1") } returns Result.success(group)
        coEvery { memberRepository.getMembers("g1") } returns Result.success((1..20).map { 
            Member(id = "m$it", groupId = "g1")
        })
        coEvery { contributionRepository.getContributions("g1") } returns Result.success(contributions)
        coEvery { memberRepository.getPreviousMonthMemberCount("g1") } returns Result.success(20)
        
        // When
        val result = useCase("g1")
        
        // Then
        assert(result.isSuccess)
        val score = result.getOrThrow()
        assert(score.zone == RiskZone.GREEN)
        assert(score.overallScore > 70)
    }
    
    @Test
    fun `generates appropriate recommendations`() = runBlocking {
        // Given a YELLOW zone group
        val group = Group(id = "g1", balance = 5000.0)
        val contributions = (1..6).map {
            Contribution(amount = 1000.0, dueDate = "2026-${it:02d}-01")
        }
        
        // Setup mocks
        coEvery { groupRepository.getGroup("g1") } returns Result.success(group)
        coEvery { memberRepository.getMembers("g1") } returns Result.success((1..15).map { 
            Member(id = "m$it", groupId = "g1")
        })
        coEvery { contributionRepository.getContributions("g1") } returns Result.success(contributions)
        coEvery { memberRepository.getPreviousMonthMemberCount("g1") } returns Result.success(20)
        
        // When
        val result = useCase("g1")
        
        // Then
        assert(result.isSuccess)
        val score = result.getOrThrow()
        assert(score.recommendations.isNotEmpty())
        assert(score.recommendations.any { it.contains("Increase") || it.contains("reserves") })
    }
}
```

### Integration Tests

```kotlin
class HealthScoreIntegrationTest {
    
    @Test
    fun `end-to-end health score generation and display`() = runBlocking {
        // Simulate admin viewing health score
        val groupId = "test_group_123"
        
        // 1. Calculate score
        val score = calculateHealthScoreUseCase(groupId).getOrThrow()
        
        // 2. Save to DB
        healthScoreRepository.saveHealthScore(score).getOrThrow()
        
        // 3. Retrieve and verify
        val retrieved = healthScoreRepository.getHealthScore(groupId).getOrThrow()
        assert(retrieved.groupId == groupId)
        assert(retrieved.overallScore in 0..100)
        assert(retrieved.zone in listOf(RiskZone.RED, RiskZone.YELLOW, RiskZone.GREEN))
    }
}
```

---

## 9. Implementation Timeline

| Week | Task | Status |
|------|------|--------|
| **Week 1** | Core algorithm + models + DAO + tests | In Progress |
| **Week 2** | UI components (card + details screen) + PDF export | Planned |
| **Week 3** | Integration with AdminViewModel + Deployment | Planned |

**Estimated Effort**: 80–100 engineering hours (2–3 weeks for 1 senior engineer)

---

## 10. Success Metrics

| Metric | Target | Measurement |
|--------|--------|------------|
| **Feature Adoption** | 80% of admins view health score within 1 week | GA tracking |
| **PDF Exports** | 40% of users export report weekly | Firebase Analytics |
| **User Satisfaction** | NPS >60 on "Health Score usefulness" | In-app survey |
| **Performance** | Score calculation <2s | App Performance Monitoring |

---

*Document Version: 1.0 | Last Updated: May 5, 2026*  
*Owner: Android Tech Lead*

