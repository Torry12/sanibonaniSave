# Technical State — August 28, 2026

## 🚀 Overview
Today's update delivered a massive refactor focused on **Financial Precision**, **Module Integrity**, and **ML-Readiness**. This state resolves the critical build failures in the `:domain` module and standardizes currency handling across the platform.

## 🛠 Architectural Changes

### 1. Financial Precision Hardening
- **Model Standard**: Eradicated `Double` for financial math. All currency fields in Models, Room Entities, and Formulas now use `Money` (BigDecimal).
- **Safe Math Utilities**: Added `.ratio()` for safe percentage/division and `.dividedBy()` for allocation to the `Money` domain model.
- **Actuarial Migration**: All 6 group strategies (Burial, Stokvel, ROSCA, Investment Club, Emergency Fund, Tontine) migrated to precision-safe math.

### 2. Module & Build Integrity
- **Unified Domain Models**: Resolved "Redeclaration" and "Unresolved reference" errors by consolidating `AuditModels.kt`, `NotificationModels.kt`, and `ActuarialModels.kt`.
- **Hilt Resolution**: Replicated `@Qualifier` definitions within `:domain` to fix circular dependencies without relying on `:app` context.
- **Build Upgrade**: Upgraded to **AGP 9.3.2** to support **Hilt 2.60.1**.

### 3. ML-Ready Behavior Tracking
- **Configurable Scoring**: Scoring weights for behavior and fraud detection are now externalized.
- **Dataset Generation**: Implemented JSONL export functionality in `BehaviorTrackingRepository` supporting SFT and DPO formats for Azure AI Foundry.
- **Labelled Simulation**: The simulation tool now generates ground-truth fraud labels for training.

## 📦 Status Summary

| Module | Build Status | Precision Level |
| :--- | :--- | :--- |
| `:domain` | **SUCCESS** | High (BigDecimal) |
| `:data` | **SUCCESS** | High (BigDecimal) |
| `:app` | **SUCCESS** | High (BigDecimal) |
| `:simulation` | **SUCCESS** | High (BigDecimal) |

## 📝 Maintenance Notes
- Use `gradle :domain:assembleDebug` to verify model changes.
- Ensure all new financial fields in Room use the standardized `toPlainString()` converter.
- ViewModels should follow the **Parallel Warmup** and **Job Hardening** patterns documented in `AGENTS.md`.
