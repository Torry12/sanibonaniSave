# SanibonaniSave: Tactical Implementation Roadmap
## Converting Competitive Advantages into Market Share

---

## Quick-Win Initiatives (30–90 Days)

### **1. "Group Health Score" Launch** (30 days)
**Objective**: Showcase actuarial differentiation

**What to Build**:
- Dashboard card showing group's composite risk score (0–100)
- Breakdown: solvency ratio, loss ratio, reserve adequacy, funding ratio
- Color-coded zones (Red/Yellow/Green)
- 1-page "Customized Strategy Report" (PDF export)

**Marketing Value**:
- "The only stokvel app with actuarial scoring"
- Publish sample reports on social media
- Share case study: "Group A was 23/100 (Red Zone) → We recommended X, now 78/100"

**Tech Implementation** (Estimated 2–3 weeks):
```kotlin
// Add to PaymentCalculator or new ViabilityAnalyzer
data class GroupHealthScore(
    val overallScore: Int,         // 0–100
    val solvencyRatio: Double,     // Reserve adequacy
    val lossRatio: Double,         // Claims / Contributions
    val fundingRatio: Double,      // Assets / Liabilities
    val riskZone: RiskZone,        // RED, YELLOW, GREEN
    val recommendations: List<String>,
    val generatedAt: String
)

// In AdminViewModel
data class AdminUiState(
    // ...existing...
    val groupHealthScore: GroupHealthScore? = null,
    val showHealthCard: Boolean = true
)
```

**Distribution**:
- Enable PDF download of score report from admin dashboard
- Create WhatsApp template: "[GROUP_NAME] Health Check: Your stokvel is at 78/100 (Healthy). View full report: [LINK]"

---

### **2. "Stokvel Safety Guarantee" Claims** (45 days)
**Objective**: Position as trustworthy vs. MoMo/Capitec

**Content**:
- "Member funds are segregated & auditable"
- "Admin controls are transparent & logged"
- "All transactions encrypted end-to-end"
- Publish 1-pager: "Why SanibonaniSave is safer than WhatsApp + Spreadsheets"

**Technical Evidence**:
- Screenshot: Row-level security policies (show Supabase RLS rules)
- Video: Demo of audit logs showing all changes
- Certify: POPIA compliance (get certificate from compliance firm)

**Launch Channels**:
- Blog post on Medium
- LinkedIn thought leadership
- Distribute to 50 funeral directors as sales collateral

---

### **3. Competitive Comparison Page** (14 days)
**Objective**: Win via transparency

**Build**:
- Static webpage with feature matrix (reference COMPETITIVE_ANALYSIS above)
- Honest positioning: "We specialize in governance & actuarial; others focus on payments"
- Link from landing page: "How we compare to other stokvel apps"

**SEO Keywords**:
- "Stokvel management app South Africa"
- "Burial society software comparison"
- "ROSCA admin tool"
- "Stokvel app vs. MoMo"

**Expected Impact**: +300–500 organic visits/month within 3 months

---

---

## Mid-Term Wins (3–6 Months)

### **4. "Enterprise Governance" Tier** (12 weeks)
**Target Segment**: Formal burial societies (100+ members), NGO stokvels, cooperatives

**Premium Features**:
- **Constitution Builder**: Templated group charter with digital signature
- **Voting Workflows**: Multi-member approval for payout/loan requests
- **Audit Trail**: Complete history of all changes (exportable for compliance)
- **Treasury Reports**: GAAP-friendly balance sheets, trial balances
- **Role Customization**: Define custom roles (e.g., "Auditor", "Chaplain")

**Pricing**: R2,500/month + R10/member (min 50 members)

**Go-to-Market**:
- Partner with 5 burial societies as pilot (free for 3 months)
- Case study: "How [SOCIETY] improved compliance by 40%"
- Direct outreach to funeral industry associations

**Tech Roadmap**:
```kotlin
// New data models
@Serializable
data class GroupCharter(
    val groupId: String,
    val constitutionPdf: String?,       // S3 URL
    val maxMembers: Int,
    val meetingFrequency: String,       // Monthly, Quarterly, etc.
    val votingRules: VotingRules,
    val roles: Map<String, Role>,       // Role → Permissions matrix
    val updatedAt: String
)

data class AuditLogEntry(
    val id: String,
    val userId: String,
    val action: String,
    val changes: Map<String, String>,   // old → new
    val timestamp: String
)

// New API endpoints in GroupRepositoryImpl
suspend fun getAuditLog(groupId: String): Flow<Result<List<AuditLogEntry>>>
suspend fun generateBalanceSheet(groupId: String, date: String): Result<BalanceSheetPdf>
```

---

### **5. Smart Loan Framework v1** (10 weeks)
**Objective**: Enable controlled microloans as revenue stream

**Loan Features**:
- Member eligibility checker (6+ months, good standing)
- Loan request workflow → Admin approval
- Payment schedule with auto-deduction from contributions
- Default handling: Auto-debit from surety (accumulated contributions)
- Contract generation (PDF template)

**Monetization**: Charge 2% origination fee on loan amounts

**Pilot**: Launch with 10–15 burial societies; measure default rates

**Tech Implementation**:
```kotlin
@Serializable
data class SmartLoan(
    val id: String,
    val memberId: String,
    val groupId: String,
    val principal: Double,
    val interestRate: Double? = 0.0,    // Interest optional
    val repaymentMonths: Int,
    val monthlyPayment: Double,
    val status: LoanStatus,             // PENDING, ACTIVE, REPAID, DEFAULTED
    val paymentSchedule: List<Payment>,
    val createdAt: String
)

@Serializable
data class LoanRequest(
    val id: String,
    val memberId: String,
    val groupId: String,
    val requestedAmount: Double,
    val purpose: String,
    val approvalStatus: ApprovalStatus,
    val approvedAmount: Double? = null,
    val approvedBy: String? = null,
    val createdAt: String
)

// Use case
class ApplyForLoanUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(request: LoanRequest): Result<LoanRequest> = runCatching {
        // Check eligibility: 6+ months, good standing
        val member = memberRepository.getMember(request.memberId).getOrThrow()
        require(member.months_as_member >= 6) { "Min 6 months membership required" }
        require(member.status == MemberStatus.ACTIVE) { "Member must be in good standing" }
        
        // Create request
        loanRepository.createLoanRequest(request).getOrThrow()
    }
}
```

---

### **6. Institutional Partnership Pilot** (16 weeks)
**Target**: Capitec or TymeBank

**Pitch**: "Co-branded stokvel manager for your customers"

**Structure**:
- SanibonaniSave = Backend + actuarial engine
- Partner = Frontend + distribution + payment processing
- Revenue: 60/40 split (SanibonaniSave/Partner) or flat licensing fee

**Expected Volume**: 5,000–10,000 groups within 12 months of launch

**Legal**: Requires partnership agreement + white-label SLA

**Tactical Steps**:
1. Week 1: Contact partnerships@capitec.co.za; request meeting
2. Week 2–4: Create pitch deck + demo environment
3. Week 5–8: Negotiation + MOU signing
4. Week 9–16: Technical integration + co-marketing plan

---

---

## Business Model Evolution

### Current Model
```
Monthly Revenue per Group = (No. Members × R10) + One-time registration fee
Example: 40-member group = R400/month + R700 once = R5,500 Year 1
```

### Proposed Multi-Tier Model (6 months out)

| Tier | Monthly | Features | Target |
|------|---------|----------|--------|
| **Free** | — | Basic member portal, contribution tracking | Early-stage groups |
| **Core** | R10/member (R200–500 range) | Admin dashboard, notifications, exports | Standard stokvels |
| **Premium** | R2,500 + R10/member | Governance, voting, audit logs | Formal societies |
| **Enterprise** | Custom | White-label, API access, custom reporting | Banks/NGOs |

### Revenue Opportunity (12-month projection)
```
Base Scenario:
- 500 groups × 30 members avg × R10 = R150,000 MRR
- 20 groups on Premium × R2,500 = R50,000 MRR
- Total: R200,000 MRR (R2.4M ARR)

Aggressive Scenario (w/ partnerships):
- 5,000 groups via partner banks × 30 members × R8 (wholesale) = R1.2M MRR
- 100 Premium groups × R2,500 = R250,000 MRR
- 10 loans/month × R50k avg × 2% fee = R10,000 MRR
- Total: R1.46M MRR (R17.5M ARR)

Conservative Scenario:
- 1,000 groups × 30 members × R10 = R300,000 MRR
- 30 Premium × R2,500 = R75,000 MRR
- Total: R375,000 MRR (R4.5M ARR)
```

---

---

## Marketing & Sales Playbook

### **Sales Channels** (in priority order)

#### 1. **Vertical: Burial Societies** (Highest ROI)
- **Reach**: 4,000–5,000 funeral directors in SA
- **Approach**: Direct outreach + industry bodies (NFPA, FASO)
- **Pitch**: "Manage 10–100 stokvels without spreadsheet chaos"
- **Incentive**: Free platform for first 3 months; 50% off for year 1
- **Expected Conversion**: 50–100 funeral companies = 500–2,000 groups

#### 2. **Vertical: NGOs & Community Groups**
- **Reach**: 500–1,000 registered NPOs with savings arms
- **Approach**: LinkedIn outreach + NGO networks (NGOPULSE, Weza)
- **Pitch**: "Professional admin tool for community savings"
- **Incentive**: Non-profit discount (40% off)
- **Expected Conversion**: 20–50 NGOs = 200–500 groups

#### 3. **Partnership: Fintech/Banks**
- **Contact**: Capitec, TymeBank, Standard Bank innovation labs
- **Approach**: White-label licensing agreement
- **Est. Volume**: 5,000–10,000 groups via partner distribution
- **Timeline**: 6 months to pilot; 12 months to commercial

#### 4. **Organic/SEO**
- **Keywords**: "Stokvel app", "Burial society software", "ROSCA manager"
- **Content**: Blog posts, YouTube tutorials, case studies
- **Expected**: 300–500 leads/month at 12 months
- **Conversion Rate**: 5–10% = 30–50 new groups/month

#### 5. **Social Media**
- **Channels**: LinkedIn, WhatsApp Business, TikTok (younger audience)
- **Content**: "Group health tips", "Funeral planning guides", member testimonials
- **Budget**: R2,000–5,000/month
- **Expected**: 100–200 leads/month

---

### **Key Marketing Assets** (To Create This Quarter)

1. **Case Study**: "How a 50-member Durban burial society prevented collapse"
2. **Video**: 2-min demo showing admin + member experiences
3. **Comparison Guide**: SanibonaniSave vs. Competitors (honest positioning)
4. **ROI Calculator**: "See how much admin time you'll save"
5. **Testimonials**: Video quotes from 3–5 early users
6. **White Paper**: "Why stokvels fail (and how to prevent it)"

---

---

## Retention & Growth Strategy

### **Churn Prevention**
- **Monitor**: Groups with 0 transactions for 30+ days
- **Action**: Send admin a "check-in" message; offer free onboarding call
- **Expected Impact**: Reduce churn from 15% to 8%

### **Upsell to Premium**
- **Trigger**: Groups with 50+ members
- **Message**: "Scale your governance with Premium features"
- **Offer**: Free 1-month trial
- **Expected Conversion**: 10–15% of Core users → Premium

### **Expansion Within Groups**
- **Goal**: Increase $ per group/month (not just members)
- **Methods**:
  - Premium tier (governance, audit logs)
  - Loan facilitation (2% fee)
  - Investment products (future, 0.5% AUM)

---

---

## Q2–Q4 2026 Roadmap Summary

| Quarter | Focus | Revenue Impact | Key Deliverables |
|---------|-------|-----------------|------------------|
| **Q2** | Health scores + comparison page + pilot loans (10 groups) | +R50k MRR | 3 case studies |
| **Q3** | Premium tier launch + enterprise pilot (TymeBank/Capitec) | +R150k MRR | 5 premium groups |
| **Q4** | Partnership commercialization + full loan rollout | +R500k MRR | 50–100 partner groups |

**Total 2026 Revenue Projection**: R2M–R4M (vs. R0–R500k today)

---

## Success Metrics (OKRs)

### **Objective 1: Dominate Burial Society Segment**
- **KR1**: 200+ funeral directors registered (vs. ~20 today)
- **KR2**: 1,000+ groups on platform (vs. ~50 today)
- **KR3**: 50k+ active members (vs. ~2k today)
- **KR4**: 95%+ member satisfaction (NPS > 50)

### **Objective 2: Launch Premium Revenue Stream**
- **KR1**: 50+ groups on Premium tier
- **KR2**: R500k+ MRR from Premium (vs. R0 today)
- **KR3**: Enterprise partnerships with 2+ financial institutions

### **Objective 3: Become Fintech Credible**
- **KR1**: POPIA + FAIS compliance certificates
- **KR2**: 3+ published case studies sharing user impact
- **KR3**: SAIA actuarial endorsement letters

---

## Conclusion

SanibonaniSave's **window of opportunity is NOW**. The savings group market is:
- ✅ Underserved (weak competitors)
- ✅ Growing (3.7M+ members in SA stokvels)
- ✅ Willing to pay (R500–2,500/month per group)
- ✅ Unsophisticated (low switching costs, easy to impress)

By executing this roadmap over the next 6 months, SanibonaniSave can:
- **Capture**: 500–1,000 groups (vs. <50 today)
- **Generate**: R2M–R4M revenue (12x growth)
- **Position**: As the **category leader** in SA stokvel management
- **Enable**: Expansion into adjacent markets (Kenya, Botswana, Uganda)

**Execution is the differentiator. Move fast.**

---

*Prepared: May 5, 2026*  
*Review regularly: Monthly for first quarter, then quarterly*

