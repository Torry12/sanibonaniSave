# Member Behavior Tracking & Fraud Detection System

## Overview

The SanibonaniSave platform now includes a comprehensive **Behavior Tracking System** that monitors member activities, calculates fraud risk scores, and provides actionable insights for group administrators. The system uses member ID numbers as the primary index for efficient lookups and includes sophisticated metrics to detect fraud and risky behavior.

## Architecture

### Core Components

1. **Domain Models** (`BehaviorTracking.kt`)
   - `MemberBehaviorTrack`: Main model tracking member behavior metrics
   - `FraudDetectionEvent`: Audit trail for suspicious activities
   - `BehaviorAnalyticsSummary`: Group-level behavior analytics
   - Enums: `FraudRiskLevel`, `BehaviorStatus`

2. **Data Layer**
   - **DAOs**: `MemberBehaviorTrackDao`, `FraudDetectionEventDao`, `BehaviorAnalyticsSummaryDao`
   - **Entities**: Room database tables for persistence
   - **Repository**: `BehaviorTrackingRepositoryImpl` - implements sync between Room and Supabase

3. **Scoring Engine** (`BehaviorScoringUtils.kt`)
   - Behavior Score Calculation (0-100)
   - Fraud Score Calculation (0-100)
   - Risk Level Determination
   - Fraud Detection Algorithms

4. **ViewModel** (`BehaviorTrackingViewModel.kt`)
   - Exposes reactive state via `StateFlow`
   - Provides UI operations for flagging, suspension, etc.

## Key Metrics Tracked

### Payment Metrics (40% of Behavior Score)
- **Total Contributions**: Total number of payments made
- **On-Time Contributions**: Payments made before due date
- **Late Contributions**: Payments made after due date
- **Overdue Count**: Number of overdue items
- **Missed Contributions**: Contributions not paid
- **Payment Consistency Score**: 0-100 rating
- **Average Days Late**: Average lateness in days

### Contribution Streaks
- **Current Payment Streak**: Consecutive on-time payments
- **Longest Payment Streak**: Historical best streak
- **Broken Streak Flag**: Recently failed streak

### Financial Metrics
- **Total Amount Contributed**: Sum of all contributions
- **Total Late Fees Paid**: Late fee payments made
- **Pending Late Fees**: Outstanding late fees
- **Total Outstanding Amount**: Total money owed

### Loan Metrics (30% of Behavior Score)
- **Total Loans Requested**: Loan applications
- **Total Loans Completed**: Successfully repaid loans
- **Active Loans**: Current outstanding loans
- **Overdue Loans**: Loans with missed payments
- **Loan Default Count**: Number of defaults
- **Loan Completion Rate**: Percentage of loans completed

### Fraud Indicators (30% of Fraud Score)

#### High-Risk Indicators
- **Duplicate Transactions**: Multiple identical transactions within timeframe
- **Velocity Spike**: Unusual number of transactions in short window
- **Unusual Payment Patterns**: Sudden deviations from historical average (>200%)
- **Multiple Accounts Detected**: Same member using multiple accounts
- **Rapid Disbursement Attempts**: Multiple loan requests in short period

#### Suspicious Activities
- **Suspicious Activity Count**: Total suspicious events
- **Velocity Check Failed**: Failed rapid activity detection

### Member Tenure (10% of Behavior Score)
- **Months in Group**: Duration of membership
- **Joined At**: Join date
- **Last Activity At**: Last transaction timestamp
- **Last Contribution At**: Last payment timestamp

## Scoring System

### Behavior Score (0-100)
Composite score based on:
- Payment Consistency: 40%
- Loan Performance: 30%
- Current Standing: 20%
- Duration & Engagement: 10%

**Interpretation:**
- **85-100**: EXCELLENT - Highly trustworthy member
- **70-84**: GOOD - Reliable member
- **50-69**: FAIR - Average member
- **0-49**: POOR - Problematic member

### Fraud Score (0-100)
Risk assessment based on:
- Transaction Risk: 30%
- Pattern Risk: 25%
- Account Risk: 25%
- Historical Risk: 20%

### Fraud Risk Levels
- **LOW** (0-44): Minimal fraud risk
- **MEDIUM** (45-64): Moderate fraud risk - monitor
- **HIGH** (65-84): High fraud risk - review recommended
- **CRITICAL** (85-100): Critical fraud risk - action required

## Usage Examples

### 1. Load Member Behavior
```kotlin
// In your UI/Fragment
val viewModel: BehaviorTrackingViewModel by viewModels()

// Load behavior for specific member
viewModel.loadMemberBehavior(memberId)

// Observe state
lifecycleScope.launch {
    viewModel.state.collect { state ->
        val track = state.memberBehavior
        // Use track data for display
    }
}
```

### 2. Query by Member ID Number
```kotlin
// Find member by ID number (useful for admin lookup)
viewModel.loadMemberBehaviorByIdNumber(idNumber = "9504234567", groupId = groupId)
```

### 3. Monitor High-Risk Members
```kotlin
// Get all high-risk members in a group
viewModel.observeHighRiskMembers(groupId)

// State will update with list of high-risk members
state.collect { 
    val highRiskMembers = it.highRiskMembers
}
```

### 4. Flag Member for Review
```kotlin
viewModel.flagMemberForReview(
    memberId = memberId,
    reason = "Duplicate transaction detected",
    notes = "Found matching transaction from 5 minutes ago"
)
```

### 5. Suspend Member
```kotlin
viewModel.suspendMember(
    memberId = memberId,
    reason = "Multiple fraud indicators detected"
)
```

### 6. View Group Analytics
```kotlin
// Calculate and load analytics for entire group
viewModel.loadBehaviorAnalytics(groupId)

state.collect { 
    val analytics = it.analytics
    // Shows: excellent_members, good_members, fraud_risk_count, etc.
}
```

### 7. Record Fraud Event
```kotlin
val fraudEvent = FraudDetectionEvent(
    memberId = memberId,
    groupId = groupId,
    eventType = "duplicate_transaction",
    severity = FraudRiskLevel.HIGH,
    details = mapOf(
        "first_transaction_id" to txId1,
        "second_transaction_id" to txId2,
        "amount" to "250.00",
        "time_difference_minutes" to "5"
    ),
    actionTaken = null
)

viewModel.recordFraudEvent(fraudEvent)
```

## Database Schema

### member_behavior_track Table
| Column | Type | Description |
|--------|------|-------------|
| id | TEXT PRIMARY KEY | Unique identifier |
| member_id | TEXT UNIQUE | Member reference (indexed) |
| member_id_number | TEXT | ID number for lookup (indexed) |
| group_id | TEXT | Group reference (indexed) |
| fraud_risk_level | TEXT | Risk level (indexed) |
| behavior_score | REAL | 0-100 score |
| fraud_score | REAL | 0-100 score |
| is_flagged_for_review | BOOLEAN | Flag status (indexed) |

### fraud_detection_events Table
| Column | Type | Description |
|--------|------|-------------|
| id | TEXT PRIMARY KEY | Event ID |
| member_id | TEXT | Member reference |
| event_type | TEXT | Type of fraud event |
| severity | TEXT | CRITICAL/HIGH/MEDIUM/LOW |
| resolved | BOOLEAN | Whether resolved |
| details_json | TEXT | Event details (JSON) |

### behavior_analytics_summary Table
| Column | Type | Description |
|--------|------|-------------|
| group_id | TEXT PRIMARY KEY | Group reference |
| average_behavior_score | REAL | Group average |
| high_fraud_risk_count | INT | Members at HIGH/CRITICAL |
| on_time_payment_rate | REAL | Percentage |

## Fraud Detection Algorithms

### Duplicate Transaction Detection
```kotlin
// Checks if transaction matches:
// - Same member
// - Same amount
// - Within time window (default: 60 minutes)
BehaviorScoringUtils.detectDuplicateTransaction(transactions, newTx, timeWindowMinutes = 60)
```

### Velocity Spike Detection
```kotlin
// Detects unusual activity rate:
// - Default: More than 5 transactions in 30 minutes
BehaviorScoringUtils.detectVelocitySpike(
    transactions, 
    memberId,
    maxTransactionsInWindow = 5,
    timeWindowMinutes = 30
)
```

### Unusual Payment Pattern Detection
```kotlin
// Detects sudden changes in payment behavior:
// - Compares recent 10 contributions
// - Flags if diff > 200% from average
BehaviorScoringUtils.detectUnusualPaymentPattern(contributions, memberId)
```

## Auto-Flagging Rules

A member is automatically flagged for review if:
- Fraud score ≥ 50
- ≥ 2 loan defaults
- ≥ 3 overdue contributions AND behavior score < 50
- Multiple accounts detected
- Unusual patterns detected AND behavior score < 60

## Auto-Suspension Rules

A member is automatically suspended if:
- Fraud score ≥ 80
- ≥ 3 loan defaults
- ≥ 5 overdue contributions AND behavior score < 30
- Multiple accounts + velocity check failures
- Already suspended (persistent)

## Migration & Database Versioning

Database version bumped from 38 to 39 for these new tables:
- `member_behavior_track` (new)
- `fraud_detection_events` (new)
- `behavior_analytics_summary` (new)

Migration applied automatically on app update.

## Performance Considerations

1. **Indexed Lookups**: member_id_number index allows fast member ID lookups
2. **Lazy Loading**: Scores calculated only when needed (not on every save)
3. **Flow-based Updates**: Reactive streams avoid polling
4. **Batch Operations**: Supports bulk score recalculation for groups

## Future Enhancements

1. **Machine Learning**: Integrate ML models for improved fraud detection
2. **Historical Trending**: Track score changes over time for pattern analysis
3. **Custom Rules**: Allow group admins to define custom fraud rules
4. **Integration**: Connect with external fraud databases
5. **Webhooks**: Trigger alerts on critical fraud events
6. **Export**: CSV/PDF reports for regulators

## API Reference

### BehaviorTrackingRepository Interface

```kotlin
// Observe reactive streams
fun observeMemberBehavior(memberId: String): Flow<Result<MemberBehaviorTrack?>>
fun observeHighRiskMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>
fun observeFlaggedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>
fun observeFraudEventsByMember(memberId: String): Flow<Result<List<FraudDetectionEvent>>>

// Get single values
suspend fun getMemberBehavior(memberId: String): Result<MemberBehaviorTrack?>
suspend fun getMemberBehaviorByIdNumber(idNumber: String, groupId: String): Result<MemberBehaviorTrack?>

// Calculate scores (expensive operation)
suspend fun calculateAndUpdateMemberBehavior(memberId: String, groupId: String): Result<MemberBehaviorTrack>
suspend fun recalculateGroupBehaviorScores(groupId: String): Result<Unit>

// Member actions
suspend fun flagMemberForReview(memberId: String, reason: String): Result<Unit>
suspend fun suspendMember(memberId: String, reason: String): Result<Unit>

// Analytics
suspend fun calculateBehaviorAnalytics(groupId: String): Result<BehaviorAnalyticsSummary>
suspend fun getMembersBehaviorStats(groupId: String): Result<Map<String, Any>>
```

## Support & Troubleshooting

**Issue**: Behavior scores not updating
- **Solution**: Call `calculateAndUpdateMemberBehavior()` explicitly; automatic updates only on member actions

**Issue**: Member incorrectly flagged
- **Solution**: Check fraud score calculation; review fraud events; adjust thresholds if needed

**Issue**: Slow performance with large groups
- **Solution**: Use indexed queries; avoid recalculating entire group frequently; batch operations

---

**Last Updated**: May 2026  
**System Version**: 1.0

