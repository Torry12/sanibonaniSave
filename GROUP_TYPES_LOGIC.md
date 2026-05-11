# 📂 Group Type Specific Business Logic

This document defines the specialized business rules and financial logic applied to each group type in SanibonaniSave.

---

## 1. Burial Society (Funeral Insurance)
*Primary Goal: Cover funeral expenses for members and their beneficiaries.*

### 💸 Premium Logic
- **Base Premium**: Fixed monthly contribution.
- **Risk-Adjusted Surcharge**: Monthly contribution increases by a percentage (defined in `groups.beneficiary_increase_pct`) for every beneficiary over the age of 65.
- **Override**: Administrators can manually set a `monthly_contribution_override` per member.

### ⏳ Waiting Periods (Probation)
- **Accidental Death**: Benefits are available immediately upon joining and paying the first contribution.
- **Natural Death**: 6-month waiting period (`probation_months`) applies. No claims can be made for natural death during this time.
- **Suicide**: 12-month waiting period applies.

### 📋 Claim Logic
- **Benefit Cap**: Payout is capped at a fixed amount or a multiple of contributions depending on group settings.
- **Validation**: Requires death certificate and ID of the deceased beneficiary.

---

## 2. Stokvel (Traditional Savings)
*Primary Goal: Periodic payouts for social events, groceries, or lump-sum savings.*

### 💰 Contribution Logic
- **Fixed Monthly**: Most common; everyone pays the same.
- **Flexible**: Members can contribute more to increase their share of the end-of-year payout.

### 🔄 Payout Models & Projections
- **Annual Payout Projection**: Calculated via `CalculateStokvelPayoutsUseCase`. It estimates the year-end fund total based on `(group.balance + (expected_monthly_total * months_remaining))`. 
- **Member Shares**: Each member sees their projected final payout based on their current `total_paid` plus scheduled future contributions.

---

## 3. ROSCA (Rotating Savings and Credit Association)
*Primary Goal: Peer-to-peer interest-free lending via rotation.*

### 🎡 Rotation Logic
- **Automated Scheduling**: Handled by `CalculateRoscaRotationUseCase`. It sorts members by joined date and assigns a specific payout month to each.
- **Pot Total**: Calculated as `group.monthly_contribution * members.count`.
- **Visibility**: Both Admins and Members see a visual timeline of completed, current, and upcoming recipients in the cycle.

---

## 4. Investment Club
*Primary Goal: Wealth creation through pooled capital.*

### 📈 Valuation & Equity
- **Net Asset Value (NAV)**: The current `group.balance`.
- **Unit Price**: Initially 1.0 (R1 per unit). Fluctuates as `group.balance / total_member_contributions`.
- **Member Valuation**: Proportional ownership calculated by `CalculateInvestmentClubValuationUseCase`. Shows members the current market value of their shares relative to their cost basis.

---

## 5. Emergency Fund
*Primary Goal: Quick access to funds for unexpected life events.*

### ⚡ Disbursal Rules
- **Withdrawal Validation**: Managed by `ProcessEmergencyWithdrawalUseCase`.
- **Limits**: Maximum single withdrawal capped at 50% of available group liquidity to ensure fund survival.
- **Eligibility**: Restricted to `ACTIVE` status members only.

---

## 📊 Summary of Financial Parameters by Type

| Feature | Burial Society | Stokvel | ROSCA | Investment Club | Emergency Fund |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Waiting Period** | 6 Months | None | None | 12 Months | None |
| **Risk Premium** | Yes (Age) | No | No | No | No |
| **Payout Basis** | Benefit Amt | Total Saved | Fixed Pot | Share % | Personal Bal |
| **Loans** | Allowed | Allowed | Not Applic. | Restricted | Allowed |
| **Admin Fee** | High (Actuarial) | Low | Low | Medium | Low |
