# 📑 Documentation Index — SanibonaniSave

Welcome to the SanibonaniSave documentation portal. This index organizes all project specifications, guides, and reports.

---

## 🏛️ Core Architecture & Reference
- **[AGENTS.md](../AGENTS.md)**: **AI Coding Reference.** Engineering principles, precision-safe patterns, and module boundaries. Start here for coding standards.
- **[APPLICATION.md](../APPLICATION.md)**: **Complete Application Reference.** Primary manual for maintenance, architecture, and future upgrades.
- **[README.md](../README.md)**: High-level summary and quick setup.
- **[RULES.md](RULES.md)**: Project coding standards, architectural boundaries, and best practices.
- **[DATA_ARCHITECTURE.md](DATA_ARCHITECTURE.md)**: Deep dive into data structures, database schema, and offline-first synchronization.
- **[CONTEXT.md](CONTEXT.md)**: Product context, vision, and high-level project goals.
- **[APP_ARCHITECTURE_AND_TECHNICAL_GUIDE.md](APP_ARCHITECTURE_AND_TECHNICAL_GUIDE.md)**: Detailed technical stack and layered architecture overview.
- **[AI_DEVELOPMENT_GUIDE.md](AI_DEVELOPMENT_GUIDE.md)**: Workflow and tooling guidance for AI agents.

---

## 📊 Business Logic & Systems
- **[BUSINESS_PROCESSES.md](BUSINESS_PROCESSES.md)**: Business rules, member/loan/payout lifecycles, and process flows.
- **[APP_STRUCTURE_BUSINESS_LOGIC_DEEP_ANALYSIS_2026-05-14.md](APP_STRUCTURE_BUSINESS_LOGIC_DEEP_ANALYSIS_2026-05-14.md)**: Deep dive into the internal mechanics of group types and financial rules.
- **[LOAN_RULES.md](LOAN_RULES.md)**: Credit rules, eligibility and amortization policy.
- **[KYC_DOCUMENTS.md](KYC_DOCUMENTS.md)**: Identity verification requirements and document handling.
- **[AUTOMATED_DOCUMENTS.md](AUTOMATED_DOCUMENTS.md)**: Contract and constitution generation.
- **[BACKEND_ORGANIZATION.md](BACKEND_ORGANIZATION.md)**: Supabase structure, RLS policies, and database schema organization.
- **[SQL_DOCUMENTATION.md](../supabase/SQL_DOCUMENTATION.md)**: Tables, column CHECK vocabulary, core RPCs, error codes, balance invariants, and the dated `SYNC_DELTA` workflow.
- **[README_LEDGER.md](../supabase/docs/README_LEDGER.md)**: The ledger-first policy — what may write `groups.balance` and how to repair drift.
- **[README_GROUP_VOTING.md](../supabase/docs/README_GROUP_VOTING.md)**: Poll tables, the four voting triggers, and the single-admin authorization model.
- **[IMPERSONATION_FLOW.md](IMPERSONATION_FLOW.md)**: Technical guide for Platform Admin impersonation features for support.
- **[FINANCIAL_ARCHITECTURE_REFACTOR_2026.md](FINANCIAL_ARCHITECTURE_REFACTOR_2026.md)**: Detailed report of the August 2026 reorganization.

---

## 📈 Current Technical State
- **[TECHNICAL_STATE_LATEST.md](TECHNICAL_STATE_LATEST.md)**: **Authoritative current status** (October 2026).
- [TECHNICAL_STATE_2026-08-29.md](TECHNICAL_STATE_2026-08-29.md): System status.
- [TECHNICAL_STATE_2026-08-28.md](TECHNICAL_STATE_2026-08-28.md): System status.
- [TECHNICAL_STATE_2026-08-21.md](TECHNICAL_STATE_2026-08-21.md): System status (Context Hardening & Quality Refactor).
- [TECHNICAL_STATE_2026-08-12.md](TECHNICAL_STATE_2026-08-12.md): System status (Post-Integration Hardening).
- [TECHNICAL_STATE_2026-08-08.md](TECHNICAL_STATE_2026-08-08.md): System status prior to precision hardening.

---

## 🚀 Operations & Deployment
- **[SEED_DATA_INSTRUCTIONS.md](SEED_DATA_INSTRUCTIONS.md)**: How to seed local and remote databases.
- **SQL parity probes**: `../supabase/maintenance/SCHEMA_PARITY_DIGEST.sql` (one-row object counts + digest, expected baseline in its header) and `SCHEMA_OBJECT_FINGERPRINTS.sql` (per-function body hashes) are the read-only way to measure Local↔repo↔Cloud drift.
- **[SIMULATION_RESULTS.md](SIMULATION_RESULTS.md)**: Live simulation runs and their outcomes.
- **[DELIVERABLES_RUNTIME_SIGNAL.md](DELIVERABLES_RUNTIME_SIGNAL.md)**: Details on the CI/CD runtime signal and diagnostic probes.
- **[QA_LOGIN_CREDENTIALS.md](../QA_LOGIN_CREDENTIALS.md)**: Test accounts for QA.

---

## 📂 Data Schemas
- **[event-schemas/](event-schemas/)**: JSON schemas for all domain events emitted by the system.
- **[markdown_docs/](markdown_docs/)**: Additional generated documentation components.

---

## 📜 Historical Reports
- **[archived_reports/](archived_reports/)**: Previous analysis reports and milestone summaries.
