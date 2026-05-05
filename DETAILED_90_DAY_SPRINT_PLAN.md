# SanibonaniSave: Detailed 90-Day Sprint Plan
**Q2–Q3 2026 Execution Blueprint**

---

## Overview

**Goal**: Generate R300k–R500k MRR and establish market leadership  
**Duration**: 13 weeks (May 6 – August 22, 2026)  
**Teams**: Engineering (4), Sales (2), Product (1), Marketing (1), Legal/Compliance (1)

---

## Sprint Structure

- **Sprint 1** (May 6–19): Foundations (2 weeks)
- **Sprint 2** (May 20–June 2): MVP Launch (2 weeks)
- **Sprint 3** (June 3–16): Sales Acceleration (2 weeks)
- **Sprint 4** (June 17–30): Premium Tier (2 weeks)
- **Sprint 5** (July 1–14): Partnership & Loans (2 weeks)
- **Sprint 6–7** (July 15–Aug 22): Scale & Optimize (4 weeks)

---

## SPRINT 1: Foundations (May 6–19)

### Goals
- Health Score engine fully functional
- Funeral industry outreach list built
- Compliance audit complete
- Website updated with competitive comparison

### Engineering Tasks

#### Task 1.1: Health Score Engine (Actuarial Logic)
**User Story**: As an admin, I want to see my group's health score so I can understand viability risks.

**Acceptance Criteria**:
- [ ] Composite risk score calculated (0–100 range)
- [ ] Component scores visible (solvency, loss ratio, reserve, funding)
- [ ] Color-coded zones (Red <40, Yellow 40–70, Green >70)
- [ ] 1-page PDF report exportable
- [ ] Score updates daily via background job

**Implementation Details**:
```kotlin
// PaymentCalculator.kt additions
object GroupHealthScoreCalculator {
    fun calculateCompositeScore(
        solvencyRatio: Double,      // 0–200%, ideal 100%
        lossRatio: Double,          // 0–100%, ideal <50%
        reserveAdequacy: Double,    // months of reserves
        fundingRatio: Double,       // assets/liabilities
        memberRetention: Double     // % retained from last month
    ): GroupHealthScore {
        // Weighted average with penalties for extreme values
        val solvencyScore = (solvencyRatio / 1.0).coerceIn(0.0, 1.0) * 25   // 0–25 points
        val lossScore = (1 - lossRatio / 1.0).coerceIn(0.0, 1.0) * 25       // 0–25 points
        val reserveScore = (reserveAdequacy / 6.0).coerceIn(0.0, 1.0) * 30  // 0–30 points
        val fundingScore = (fundingRatio / 1.0).coerceIn(0.0, 1.0) * 15     // 0–15 points
        val retentionScore = memberRetention * 5                            // 0–5 points
        
        val total = (solvencyScore + lossScore + reserveScore + fundingScore + retentionScore).toInt()
        return GroupHealthScore(
            overallScore = total,
            zone = when {
                total < 40 -> RiskZone.RED
                total < 70 -> RiskZone.YELLOW
                else -> RiskZone.GREEN
            },
            components = mapOf(
                "Solvency" to solvencyScore.toInt(),
                "Loss Ratio" to lossScore.toInt(),
                "Reserve" to reserveScore.toInt(),
                "Funding" to fundingScore.toInt(),
                "Retention" to retentionScore.toInt()
            )
        )
    }
}
```

**Dependencies**:
- Existing `PaymentCalculator` functions
- Group financial data (balance, contributions, claims)

**Testing**:
- Unit test: 10 scenarios (red, yellow, green zones)
- Integration test: Calculate score for mock group

**Estimate**: 5 days | Owner: Senior Android Engineer

---

#### Task 1.2: Health Score UI (Admin Dashboard Card)
**User Story**: As an admin, I want to see my health score on a dashboard card with actionable recommendations.

**Acceptance Criteria**:
- [ ] Dashboard card with circular progress indicator (0–100)
- [ ] Color-coded (red/yellow/green) based on zone
- [ ] Tap-through to detailed breakdown page
- [ ] "Recommendations" section (3–5 actionable items)
- [ ] "Generate Report" button → PDF download

**Implementation**:
```kotlin
// New Composable: GroupHealthScoreCard
@Composable
fun GroupHealthScoreCard(
    score: GroupHealthScore,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Group Health Score", style = MaterialTheme.typography.titleMedium)
            
            // Circular progress
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(
                        color = score.zone.backgroundColor(),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${score.overallScore}/100",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            
            // Recommendations
            Text("Recommendations:", style = MaterialTheme.typography.labelLarge)
            score.recommendations.forEach { rec ->
                Text("• $rec", style = MaterialTheme.typography.bodySmall)
            }
            
            // Action buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClick) { Text("Details") }
                Button(onClick = { /* PDF export */ }) { Text("Report") }
            }
        }
    }
}

// New Screen: HealthScoreDetailScreen (tapped from card)
@Composable
fun HealthScoreDetailScreen(score: GroupHealthScore) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Component breakdown (pie chart or bar chart)
        BarChart(
            data = listOf(
                "Solvency" to score.components["Solvency"]?.toFloat() ?: 0f,
                "Loss Ratio" to score.components["Loss Ratio"]?.toFloat() ?: 0f,
                "Reserve" to score.components["Reserve"]?.toFloat() ?: 0f,
                "Funding" to score.components["Funding"]?.toFloat() ?: 0f,
                "Retention" to score.components["Retention"]?.toFloat() ?: 0f
            )
        )
        
        // Zone explanation
        Text(
            text = when (score.zone) {
                RiskZone.RED -> "Your group is at risk. Action required within 30 days."
                RiskZone.YELLOW -> "Your group is stable but needs attention."
                RiskZone.GREEN -> "Your group is in excellent standing."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        
        // Recommendations with action buttons
        score.recommendations.forEach { (title, action) ->
            RecommendationCard(title, action)
        }
    }
}
```

**Dependencies**: GroupHealthScoreCard engine, charting library (Vico or custom)  
**Estimate**: 4 days | Owner: Android UI Engineer

---

#### Task 1.3: Health Score PDF Export
**User Story**: As an admin, I want to download a 1-page PDF report showing my group's health score so I can share with members.

**Acceptance Criteria**:
- [ ] PDF template created (SanibonaniSave branded)
- [ ] Includes: Score, components, zone, recommendations
- [ ] Filename: `{group_name}_health_report_{date}.pdf`
- [ ] Saved to downloads folder
- [ ] Error handling for file system issues

**Implementation**:
```kotlin
// NewClass: HealthScorePdfGenerator
class HealthScorePdfGenerator(context: Context) {
    fun generatePdf(score: GroupHealthScore, group: Group): Result<File> = runCatching {
        val fileName = "${group.name}_health_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        // Use iText library (add to build.gradle)
        val document = Document(PageSize.A4)
        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        
        // Header
        document.add(
            Paragraph("SanibonaniSave Health Report")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(16f)
        )
        document.add(Paragraph("Group: ${group.name}").setFontSize(12f))
        document.add(Paragraph("Generated: ${LocalDate.now()}").setFontSize(10f))
        
        // Score section
        document.add(Paragraph("\nOverall Health Score").setFontSize(14f))
        document.add(
            Paragraph("${score.overallScore}/100 - ${score.zone.name}")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(24f)
                .setTextAlignment(TextAlignment.CENTER)
        )
        
        // Components table
        val table = Table(2)
        table.addCell("Component")
        table.addCell("Score")
        score.components.forEach { (name, value) ->
            table.addCell(name)
            table.addCell("$value/100")
        }
        document.add(table)
        
        // Recommendations
        document.add(Paragraph("\nRecommendations").setFontSize(14f))
        score.recommendations.forEach { rec ->
            document.add(Paragraph("• $rec").setFontSize(11f))
        }
        
        document.close()
        file
    }
}
```

**Dependencies**: iText library (add: `com.itextpdf:itext7-core:7.2.0`)  
**Estimate**: 3 days | Owner: Android Engineer

---

### Product Tasks

#### Task 1.4: Premium Tier Definition
**Objective**: Define what features go into Premium tier and pricing.

**Output**:
```
PREMIUM TIER FEATURES (R2,500/month + R10/member):
- Constitution builder (templated group charter)
- Voting workflows (multi-member approval for payouts)
- Audit trail (exportable, GAAP-compliant)
- Treasury reports (balance sheet, income statement)
- Role customization (define custom roles + permissions)
- Priority support (email SLA: 4 hours)

TARGET CUSTOMER: Formal societies (100+ members), NGOs, cooperatives
COMPETITOR POSITIONING: "Enterprise-grade governance for stokvels"
```

**Deliverables**:
- Feature spec document (1,000 words)
- Competitor comparison (Premium vs. Core)
- Pricing justification model
- Sales deck slide

**Estimate**: 2 days | Owner: Product Manager

---

### Marketing Tasks

#### Task 1.5: Funeral Industry Outreach List
**Objective**: Build database of 500+ funeral directors for cold outreach.

**Sources**:
1. Yellow Pages (local listings)
2. Industry associations (NFPA, FASO)
3. Google Maps scrape (funeral homes in each metro)
4. LinkedIn (queries: "funeral director", "funeral home owner")

**Deliverables**:
- CSV: name, company, email, phone, city, size (small/medium/large)
- Email templates (3 variants: intro, value prop, offer)
- Calendar: 100 outreach/week starting Week 2

**Estimate**: 3 days | Owner: Sales/Marketing Lead

---

#### Task 1.6: Website Update (Competitive Comparison)
**Objective**: Launch "How we compare" page on website.

**Content**:
- Feature matrix vs. 5 competitors
- Honest positioning
- Case studies (link to blog)
- "Why choose SanibonaniSave" video (2 min)

**Deliverables**:
- Website page live
- SEO optimization (keywords: "stokvel app vs.", "burial society software")
- Social media posts (5x LinkedIn, 10x Twitter)

**Estimate**: 3 days | Owner: Marketing Lead

---

### Legal/Compliance Tasks

#### Task 1.7: POPIA Audit
**Objective**: Identify POPIA compliance gaps.

**Audit Includes**:
1. Data retention policies (how long do we keep inactive user data?)
2. Right to deletion (can members delete their accounts?)
3. Data breach reporting (SLA: 72 hours)
4. Consent management (are we asking for consent?)

**Deliverables**:
- Audit report (PDF, 5–10 pages)
- Remediation plan (priority, timeline, budget)
- Gaps list for engineering team

**Estimate**: 4 days | Owner: Legal/Compliance Officer

---

### Sprint 1 Summary

| Deliverable | Owner | Status |
|------------|-------|--------|
| Health Score Engine | Eng | Due May 15 |
| Health Score UI | Eng | Due May 17 |
| PDF Export | Eng | Due May 19 |
| Premium Tier Spec | Product | Due May 12 |
| Funeral Outreach List | Sales | Due May 15 |
| Website Comparison Page | Marketing | Due May 17 |
| POPIA Audit | Legal | Due May 19 |

**Sprint 1 Success Criteria**:
- ✅ All items > 90% complete
- ✅ No blockers for Sprint 2

---

## SPRINT 2: MVP Launch (May 20–June 2)

### Goals
- Health Score deployed to production
- First 50+ funeral directors contacted
- Premium tier design finalized

### Engineering Tasks

#### Task 2.1: Health Score Production Deployment
**Acceptance Criteria**:
- [ ] Feature flag enabled for 10% of users (canary)
- [ ] Monitoring alerts set up (error rate, latency)
- [ ] Gradual rollout: 10% → 50% → 100% over 3 days
- [ ] Fallback plan if errors spike

**Deliverables**:
- Deployment checklist
- Release notes
- Rollback plan

**Estimate**: 2 days | Owner: DevOps/Android Lead

---

#### Task 2.2: Smart Loan Framework Design (Not Yet Implemented)
**User Story**: As an admin, I want to request a loan from the group so the group can manage borrowing.

**Scope (Planning Only)**:
- Eligibility checker (6+ months, good standing)
- Loan request workflow (pending → approved → active)
- Repayment schedule generation
- Default handling (auto-debit from contributions)

**Output**: Technical design document (1,500 words)

**Estimate**: 3 days | Owner: Tech Lead

---

### Sales Tasks

#### Task 2.3: Funeral Director Outreach Campaign (Wave 1)
**Objective**: Schedule 20+ discovery calls with funeral directors.

**Process**:
1. Day 1–2: Email out 100 "intro" messages (templates pre-made)
2. Day 3–4: Phone calls to non-responders (warm follow-up)
3. Day 5–10: Schedule 20+ calls (aim for 5+ per week x 4 weeks)

**Pitch Script**:
```
"Hi [Name], this is [Your Title] from SanibonaniSave.

We've noticed funeral homes manage 10–100+ stokvels/savings groups each. 
Most admins use WhatsApp groups + spreadsheets (risky, no audit trail).

We built a free app that:
- Takes 2 mins to set up
- Sends automatic WhatsApp updates to members
- Shows you which groups are healthy and which are at risk
- Complies with tax law

We're offering 3 free months + 50% off year 1 as an early adopter.

Can I show you a 5-minute demo Tuesday at 2pm?"
```

**Acceptance Criteria**:
- [ ] 100+ outreach emails sent
- [ ] 20+ discovery calls scheduled
- [ ] 3+ groups signed up (paid or pilot)

**Estimate**: 8 days | Owner: VP Sales (external hire or founder)

---

### Product/Marketing Tasks

#### Task 2.4: Case Study Template & Launch
**Objective**: Create reusable case study format; publish first one.

**Template**:
- Company profile (name, group size, location)
- Challenge ("Managing multiple stokvels via spreadsheets")
- Solution ("SanibonaniSave automation")
- Results ("Saved 5 hrs/month admin time, zero errors")
- Quote from admin

**First Case Study**: Partner with earliest paying customer (due end of June)

**Deliverables**:
- Case study template (PDF, 1 page)
- First case study draft
- Blog post version
- LinkedIn post

**Estimate**: 3 days | Owner: Marketing Lead

---

### Sprint 2 Summary

| Deliverable | Owner | Status |
|------------|-------|--------|
| Health Score Production | Eng | Due May 22 |
| Loan Framework Design | Eng | Due May 24 |
| Funeral Outreach Wave 1 | Sales | Due June 2 |
| Case Study Template | Marketing | Due May 28 |

**Sprint 2 Success Criteria**:
- ✅ Health Score live in production (0 critical bugs)
- ✅ 20+ discovery calls scheduled
- ✅ 3–5 paid customers acquired

---

## SPRINT 3: Sales Acceleration (June 3–16)

### Goals
- Acquire 50–100 groups (20–30 from funeral directors, 20–30 organic)
- Premium tier beta launch (5–10 early adopters)
- API design spec completed

### Sales Tasks

#### Task 3.1: Funeral Director Sales Blitz
**Process**:
- Week 1: 20+ discovery calls → qualify 10–15
- Week 2: Product demos → close 5–10 paid deals

**Target**:
- 50–100 MRR from funeral director channel

**Incentive**:
- Free for 3 months (showing value)
- 50% off after trial (lock in long-term)

**Estimate**: Ongoing | Owner: VP Sales

---

#### Task 3.2: NGO/Cooperative Outreach (Secondary Channel)
**Objective**: Build pipeline for Premium tier.

**Targets**: 20–30 formal groups, nonprofits with 50+ members

**Approach**: LinkedIn + direct email to directors

**Estimate**: 4 days | Owner: Sales Lead

---

### Engineering Tasks

#### Task 3.3: Premium Tier MVP (Voting + Audit Logs)
**Features**:
1. **Voting Workflow**: Admin proposes payout → all members vote (yes/no)
2. **Audit Trail**: Every action logged (who, what, when, why)

**Implementation**:
```kotlin
// New models
@Serializable
data class Vote(
    val id: String,
    val proposalId: String,
    val memberId: String,
    val vote: VoteOption,  // YES, NO, ABSTAIN
    val votedAt: String,
    val reason: String? = null
)

@Serializable
data class AuditLogEntry(
    val id: String,
    val groupId: String,
    val userId: String,
    val action: String,     // "PAYOUT_APPROVED", "MEMBER_ADDED", etc.
    val changesBefore: Map<String, Any>?,
    val changesAfter: Map<String, Any>?,
    val timestamp: String,
    val ipAddress: String? = null
)

// New DAO methods
@Dao
interface VoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: Vote)
    
    @Query("SELECT * FROM votes WHERE proposal_id = :proposalId")
    fun observeVotes(proposalId: String): Flow<List<Vote>>
    
    @Query("SELECT COUNT(*) FROM votes WHERE proposal_id = :proposalId AND vote = 'YES'")
    suspend fun countYesVotes(proposalId: String): Int
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insertLog(entry: AuditLogEntry)
    
    @Query("SELECT * FROM audit_logs WHERE group_id = :groupId ORDER BY timestamp DESC LIMIT 100")
    fun observeLogs(groupId: String): Flow<List<AuditLogEntry>>
}

// New UseCase
class CreatePayoutVotingProposalUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(
        groupId: String,
        amount: Double,
        proposedBy: String
    ): Result<Proposal> = runCatching {
        val proposal = Proposal(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            type = ProposalType.PAYOUT,
            amount = amount,
            proposedBy = proposedBy,
            status = ProposalStatus.VOTING,
            createdAt = Clock.System.now().toString(),
            votingDeadline = (Clock.System.now() + 7.days).toString()
        )
        
        payoutRepository.createProposal(proposal).getOrThrow()
        auditRepository.logAction(
            AuditLogEntry(
                groupId = groupId,
                userId = proposedBy,
                action = "PROPOSAL_CREATED",
                changesAfter = mapOf("proposal" to proposal)
            )
        ).getOrThrow()
        
        proposal
    }
}
```

**Estimate**: 8 days | Owner: Senior Android Engineer

---

#### Task 3.4: Audit Trail Reporting Screen
**User Story**: As an admin, I want to export a full audit trail so I can submit it to an auditor.

**Acceptance Criteria**:
- [ ] List of last 100 actions (paginated)
- [ ] Filter by date range
- [ ] Filter by action type
- [ ] Export to CSV (spreadsheet format)
- [ ] Export to PDF (formatted report)

**Estimate**: 4 days | Owner: Android UI Engineer

---

#### Task 3.5: API v1.0 Design (Not Yet Implemented)
**Objective**: Design REST API for white-label bank partners.

**Endpoints**:
```
GET /api/v1/groups/{groupId}
GET /api/v1/groups/{groupId}/members
GET /api/v1/groups/{groupId}/health-score
POST /api/v1/groups/{groupId}/contributions
GET /api/v1/groups/{groupId}/statements
```

**Output**: OpenAPI spec + design doc

**Estimate**: 3 days | Owner: Tech Lead

---

### Sprint 3 Summary

| Deliverable | Owner | Status |
|------------|-------|--------|
| Premium Tier MVP | Eng | Due June 14 |
| Audit Trail Reporting | Eng | Due June 15 |
| API v1.0 Design | Eng | Due June 12 |
| Funeral Sales (50–100) | Sales | Due June 16 |
| NGO Outreach | Sales | Due June 16 |

**Sprint 3 Success Criteria**:
- ✅ 50–100 groups acquired (R150k–300k MRR)
- ✅ Premium tier ready for beta
- ✅ API design approved

---

## SPRINT 4–7: Scale & Optimize (July–August)

### Overview
- Premium tier commercial launch
- Bank partnership pilots
- Smart Loan framework MVP
- Scaling to 1,000+ groups

*(Detailed tasks omitted for brevity; follow same format as Sprints 1–3)*

---

## Resource Allocation Summary

| Team | Sprint 1 | Sprint 2 | Sprint 3 | Sprints 4–7 | Total |
|------|---------|---------|---------|-----------|-------|
| Engineering (4) | 20 days | 10 days | 20 days | 60 days | 110 days |
| Sales (2) | 3 days | 8 days | 10 days | 40 days | 61 days |
| Product (1) | 2 days | 3 days | 0 days | 10 days | 15 days |
| Marketing (1) | 3 days | 3 days | 3 days | 20 days | 29 days |
| Legal/Compliance (1) | 4 days | 0 days | 2 days | 10 days | 16 days |

---

## Key Metrics to Achieve

| Metric | Sprint 1 | Sprint 3 | Sprint 7 |
|--------|---------|---------|---------|
| **Groups Onboarded** | 5–10 | 50–100 | 500–1,000 |
| **MRR** | R50k | R150k–300k | R750k–1M |
| **DAU** | 500 | 5k | 25k+ |
| **NPS** | N/A | 30+ | 50+ |

---

## Success & Failure Scenarios

### ✅ Success Path
- Health Score ships on time → positive user feedback
- Funeral director channel acquires 50+ groups by end of June
- Premium tier beta generates R50k MRR
- API design enables bank partnership pilots
- → Hit R300k MRR by end of Sprint 3

### ❌ Failure Modes
- Health Score has critical bug in production → rollback 3 days
- Funeral director acquisition stalls (need to pivot to LinkedIn/organic)
- Premium tier feature complexity causes delays → launch stripped MVP instead
- Bank partnerships move slowly → focus on organic + NGO channels

**Contingency Budget**: +2 weeks per sprint for unforeseen issues

---

## Governance & Standups

**Daily Standup**: 9:30 AM (15 min)  
**Sprint Planning**: Monday 10 AM (1 hour)  
**Sprint Review/Retro**: Friday 4 PM (1.5 hours)  
**Exec Sync**: Thursday 3 PM (30 min, leadership only)

---

## Next Phase Planning

**Sprints 8–13 (Sep–Nov 2026)**:
- Smart Loans commercial launch
- Partnership commercialization (5k+ groups)
- Regional expansion prep (Kenya)
- Series A fundraising

---

*Document Version: 1.0 | Last Updated: May 5, 2026*  
*Owned by: CTO + VP Product*

