# 🏦 SanibonaniSave — SQL Database Documentation

**Version**: 7.4 (October 28, 2026 — column-level CHECK vocabulary, trigger inventory and client↔tier parity notes)
**Platform**: Supabase (PostgreSQL) / Room (SQLite)

This document provides a comprehensive overview of the SanibonaniSave database schema, including tables, key fields, and core business logic. The local Room database mirrors this schema for offline-first capabilities.

---

## 🏗️ 1. Identity & Profiles

### `profiles`
The Supabase-side profile table, synced with Auth (`id` references `auth.users(id)` `ON DELETE CASCADE`). The app calls the same concept `user_profiles` in its Room schema — that name does not exist in Postgres, and the PostgREST client addresses `profiles`.
- `id` (UUID): Maps to `auth.users.id`.
- `full_name` (TEXT NOT NULL): `CHECK (char_length(full_name) >= 3)` — stricter than `members.full_name`, which only requires 2.
- `email` (TEXT NOT NULL): `CHECK`ed against a regex, not just `NOT NULL`.
- `phone` (TEXT): Optional secondary contact.
- `role` (TEXT, default `member`): `platform_admin`, `group_admin`, or `member`.

---

## 🏛️ 2. Group Management

### `groups`
The central container for all cooperative activities.
- `id` (UUID): Unique group identifier.
- `name` (TEXT): Group name.
- `type` (TEXT): `burial_society`, `stokvel`, `rosca`, `investment_club`, `emergency_fund`, `community_savings`, `hybrid_savings_group`, `tontine`, `other`.
- `balance` (NUMERIC(12,2) NOT NULL DEFAULT 0): Cached balance derived from `group_ledger`. `NOT NULL` since `SYNC_DELTA_20261022`; never write it directly.
- `joining_fee` (NUMERIC): One-time entry fee.
- `monthly_contribution` (NUMERIC): Recurring obligation.
- `late_fee` (NUMERIC) + `late_fee_grace_days` (default 5): Penalty for missed deadlines.
- `payment_due_day` (INTEGER, 1..28, default 28): The obligation day the ledger projects against.
- `probation_months` (INTEGER, default 3) and `max_members` (INTEGER, default 50, `> 0`): Onboarding gate and capacity limit — `RegisterMemberUseCase` refuses a join once `current_members` reaches `max_members`.
- `goal_amount` (NUMERIC): Target savings for the cycle. `period_months` (default 12) the cycle length.
- `burial_claim_amount` (default 10,000), `min_members_for_claims` (default 10), `min_reserve_percentage` (default 20): Burial Society policy controls.
- `fee_status` (TEXT): `paid`, `due`, `warning`, `suspended`, `pending_activation`, `overdue` — the platform subscription state, not a member obligation.
- `rosca_rotation_method` (TEXT): `fixed`, `random_draw`, `need_based`, `auction`.
- `loan_interest_rate` (NUMERIC(5,2)) and `loan_max_amount` (NUMERIC(12,2)), `loan_max_months` (default 12): Credit terms. Interest is `NUMERIC`, never a float. `loan_max_amount` is the per-loan ceiling: a positive value is enforced by `ValidateLoanEligibilityUseCase` at submission and again at approval, while zero/null means "not configured" and only the 30%-of-six-months rule applies.
- `constitution_url` / `constitution_status` (`pending`, `verified`, `rejected`): Governance document state.
- `is_platform_suspended` (BOOLEAN): Platform-level kill switch for the group.

### `group_polls`
Governance and voting for group decisions.
- `id` (UUID): Poll identifier.
- `group_id` (UUID): Parent group (`ON DELETE CASCADE`).
- `created_by_member_id` (UUID): Proposer; `ON DELETE SET NULL`.
- `type` (TEXT, default `general`): No CHECK — the values come from the Kotlin `PollType` enum: `general`, `fee_change`, `rosca_order`, `loan_approval`, `member_removal`.
- `status` (TEXT, default `open`): `draft`, `open`, `closed`, `cancelled` — lowercase, enforced by CHECK.
- `title` (TEXT NOT NULL, `>= 3` chars) / `description` (TEXT) / `allow_multiple_choice` (BOOLEAN, default FALSE).
- `effect_status` (TEXT, default `none`) / `effect_data` (TEXT): Whether and how the result was applied — `EffectStatus` writes `none`, `pending`, `applied`, `failed`; no CHECK on the column.
- `starts_at` / `ends_at`: `tr_sync_poll_status` (BEFORE UPDATE on `group_polls`) flips an `open` poll to `closed` once `ends_at <= NOW()` and writes a `POLL_AUTO_CLOSED` audit event as actor `system_gov`. That trigger used to be the only close path, which is why the voting screen's Close button could not work: `close_poll_v1(UUID)` is the explicit early close, declared by `SYNC_DELTA_20261029B`. It refuses a caller who is neither a platform admin nor that group's admin (`[SEC_002] POLL_CLOSE_ACCESS_DENIED`, with `service_role` exempt so the scheduler keeps working), requires `open` (`[FIN_002] BAD_STATUS`), codes a missing poll as `[SYS_001] POLL_NOT_FOUND`, and writes one `POLL_CLOSED` audit row. It never touches `vote_count` — that stays owned by `tr_sync_poll_vote_count`.

### `group_poll_options` / `group_poll_votes`
- `group_poll_options`: `label` (TEXT NOT NULL), `position` (default 1), `vote_count` (default 0). `tr_sync_poll_vote_count` (AFTER INSERT OR DELETE on votes) increments and decrements `vote_count` — never set it from the app.
- `group_poll_votes`: `poll_id` + `option_id` + `member_id` + `group_id`, all NOT NULL, with **`UNIQUE(poll_id, member_id)`**. One member may therefore record exactly one option per poll even when `allow_multiple_choice` is TRUE; the multi-choice flag is enforced only in Kotlin.
- `tr_poll_audit` / `tr_vote_audit` write `POLL_CREATED` and `VOTE_CAST` to `audit_logs` with the acting member id as `actor_id`.

---

## 👥 3. Membership & Documents

### `members`
Links users to groups with status tracking.
- `id` (UUID): Member identifier.
- `group_id` (UUID): Reference to `groups`.
- `user_id` (UUID): Direct reference to `auth.users(id)` (nullable for offline adds) — there is no FK to `profiles`.
- `status` (TEXT, default `probation`): `active`, `probation`, `suspended`, `pending_payment` — lowercase, enforced by CHECK; the domain `MemberStatus` enum maps 1:1.
- `member_key` (TEXT): Unique short-code for offline identification.
- `UNIQUE(group_id, user_id)` and `UNIQUE(group_id, member_key)`: One membership row per user per group. `tr_ensure_admin_is_member` inserts that row on group insert with `ON CONFLICT (group_id, user_id) DO NOTHING`, and `RemoteMemberDataSource.upsertMember` posts with `onConflict = "group_id,user_id"` for the same reason — a plain insert would violate it.
- `rosca_position` (INTEGER): The member's slot in the rotation order.
- `full_name` (TEXT NOT NULL, `>= 2` chars) / `id_number` (`^[0-9]{13}$` when present) / `notification_pref` (`whatsapp`, `email`, `both`).
- `total_paid` (NUMERIC(12,2), default 0): Cumulative paid contributions. `fn_sync_single_member_financials` recomputes it server-side over `status = 'paid'` rows of type `contribution`, `joining_fee`, `registration_contribution` and `loan_settlement_extraction`; because the settlement rows are negative, the column is deliberately allowed to go below zero.
- `contract_status` (TEXT): `pending`, `verified`, `rejected` — the same lowercase vocabulary as every `*_status` column on this table.

### `beneficiaries`
Registered dependents for Burial Societies.
- `member_id` (UUID): Reference to `members`; `group_id` is also NOT NULL, both `ON DELETE CASCADE`.
- `full_name` (TEXT NOT NULL, `>= 2` chars) / `id_number` (`^[0-9]{13}$` when present) / `date_of_birth` (DATE).
- `relationship` (TEXT): e.g., Spouse, Child, Parent — free-form, no CHECK.
- `is_over_65` (BOOLEAN, default FALSE): Affects actuarial risk and premiums.
- `document_status` (TEXT, default `pending`): `pending`, `verified`, `rejected` for `document_url` / `face_photo_url`.
- `UNIQUE(member_id, id)`: Note this pairs the row with its own primary key, so it does **not** cap beneficiaries per member — a member may register any number of dependents.

### `member_documents`
KYC and legal compliance files.
- `member_id` (UUID): Reference to `members`.
- `group_id` (UUID): Reference to `groups`; both are `ON DELETE CASCADE`.
- `document_type` (TEXT NOT NULL): Free-form label with no CHECK, e.g. "ID Document", "Proof of Residence".
- `document_url` (TEXT NOT NULL): Storage object for the file.
- `status` (TEXT): `pending`, `verified`, `rejected` (lowercase — the CHECK constraint is the authority).
- `verified_at` / `verified_by`: Who accepted the document and when.

### `beneficiary_payout_claims`
Burial-society claims and their supporting evidence.
- `claim_amount` (NUMERIC(12,2) NOT NULL, `> 0`): Benefit requested.
- Four evidence pairs, each `*_url` plus a `*_status` (`pending`/`verified`/`rejected`): `face_photo`, `beneficiary_id`, `death_certificate`, `member_id`. The status columns were added by `SYNC_DELTA_20261026B.sql`; existing rows took the `'pending'` default, so a claim created before that delta is explicitly pending rather than NULL.

---

## 💰 4. Financials & Ledger

### `contributions`
Records expected and paid obligations.
- `id` (UUID): Contribution ID.
- `amount` (NUMERIC(10,2) NOT NULL): The total obligation for the row.
- `status` (TEXT, default `due`): `paid`, `due`, `overdue`, `partial`, `missed` — lowercase, enforced by CHECK and re-checked by the `[FIN_003] INVALID_STATUS` guard in `record_contribution_v2`.
- `type` (TEXT, default `contribution`): `contribution`, `joining_fee`, `registration_contribution`, `late_fee`, `member_fee`, `member_fee_ledger`, `loan_settlement_extraction`. The same seven values are the whitelist the RPCs validate against.
- `payment_method` (TEXT, default `bank`): `yoco`, `stitch`, `payfast`, `bank`, `cash`, `wallet`, `other`.
- `principal_amount` (NUMERIC): The base contribution amount.
- `late_fee_amount` (NUMERIC): Accrued penalties; `late_fees_applied` flags whether they were charged.
- `UNIQUE(group_id, transaction_id)`: The idempotency key every money-moving RPC relies on.

### `payouts`
- `amount` (NUMERIC): Disbursement value.
- `status` (TEXT, default `pending`): `pending`, `processing`, `completed`, `failed`, `cancelled`, `group_approved`, `rejected`.
- `payout_type` (TEXT, default `custom`): Free-form classifier, no CHECK.
- `supporting_document_url` / `supporting_document_status`: Proof of payment or claim document plus its `pending`/`verified`/`rejected` review state.
- `metadata` (JSONB): Extensible field for specific group type data.

### `group_ledger`
**The Source of Truth.** Immutable audit trail for all balance changes.
- `id` (UUID): Ledger entry ID.
- `amount` (NUMERIC(12,2)): Change in balance (positive or negative).
- `balance_after` (NUMERIC(12,2)): Snapshot of balance after transaction.
- `category` (TEXT NOT NULL): No CHECK constraint — the value is whatever the writing RPC passes; `increment_group_balance` defaults it to `adjustment`. Observed in the masters: `contribution`, `joining_fee`, `late_fee`, `loan_settlement_extraction`, `loan_repayment`, `loan_disbursement`, `payout`, `rosca_payout_offset`, `platform_fee`.
- `parent_entry_id` (UUID): Self-reference linking related entries for traceability.
- `UNIQUE(group_id, transaction_id)`: Blocks double-ledgering. `increment_group_balance` looks the key up before writing and returns the current balance unchanged on a match; if the reused id carries a different amount, category, or member it raises `[SYS_002] TRANSACTION_ID_REUSED_WITH_DIFFERENT_LEDGER_ENTRY`.

### `bank_connections`
Secure linkage to external financial accounts.
- `group_id` (UUID): The group owning the connection.
- `provider_id` (TEXT): Link to `financial_providers`.
- `status` (TEXT): `active`, `expired`, `disconnected`, `pending`.
- `account_number_hidden` (TEXT): Masked account for display.
- `linked_at` (TIMESTAMPTZ): Connection establishment time.

### `settlements`
Tracks the clearing of funds from payment gateways.
- `transaction_id` (TEXT): Link to raw payment.
- `fee_deducted` (NUMERIC): Platform or gateway fee.
- `status` (TEXT, default `pending`): `pending`, `completed`, `failed`.

---

## 💳 5. Credit & Loans

### `loans`
Lifecycle management for credit features.
- `id` (UUID): Loan identifier.
- `amount` (NUMERIC): Principal borrowed.
- `total_to_repay` (NUMERIC): Principal + calculated interest.
- `total_repaid` (NUMERIC): Amount paid back to date.
- `amortization_type` (TEXT): `flat`, `reducing_balance`, `bullet`.
- `effective_apr` (NUMERIC): Actual cost of borrowing.
- `status` (TEXT, default `pending`): `pending`, `approved`, `active`, `partially_paid`, `completed`, `rejected`, `overdue`, `cancelled`, `defaulted` — lowercase in the database; `LoanStatus` is the Kotlin-side enum.

### `loan_repayments`
Individual payments made against an active loan.
- `loan_id` (UUID): Reference to `loans`.
- `amount` (NUMERIC): Repayment value.

---

## 📊 6. Analytics & Behavior (ML-Ready)

### `member_behavior_track`
Data points for finetuning Azure AI Foundry models.
- `payment_consistency_score` (NUMERIC(5,2), default 0): Weighted score of on-time payments.
- `behavior_score` (NUMERIC(5,2) NOT NULL, default 100.0): Aggregated reliability score (0-100).
- `fraud_score` (NUMERIC(5,2)) / `fraud_risk_level` (TEXT NOT NULL, default `low`): No CHECK; `FraudRiskLevel` serializes the lowercase forms `low`, `medium`, `high`, `critical` and the Room converter accepts either case.
- `member_status` (TEXT NOT NULL, default `fair`): Behavior banding written by `BehaviorScoringUtils`.
- `loan_default_count` (INTEGER): Number of defaulted loans; `total_loans_*`, `active_loans`, `overdue_loans` carry the rest of the credit signal.

### `fraud_detection_events`
Audit trail for suspicious activities. Room-only in the shipped app: no repository uploads this table, so the CHECK below is enforced by direct database writes.
- `event_type` (TEXT NOT NULL): No CHECK; e.g. `velocity_spike`, `duplicate_transaction`, `unusual_withdrawal`.
- `severity` (TEXT NOT NULL): `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` — UPPERCASE, enforced by CHECK. Note this is the opposite casing of `member_behavior_track.fraud_risk_level`.
- `details` (JSONB NOT NULL, default `{}`) / `action_taken` (TEXT) / `resolved` (BOOLEAN).

---

## 🔄 7. System & Sync

### `pending_operations`
Offline outbox for synchronization.
- `type` (TEXT): The RPC or operation to perform (e.g., `record_contribution`).
- `payload` (TEXT NOT NULL): JSON representation of the entity.
- `group_id` (UUID): `ON DELETE SET NULL` — a deleted group does not strand the queue.
- `retry_count` (INT) / `last_error` (TEXT): Sync-failure bookkeeping.
- `created_at` (BIGINT): Epoch milliseconds, deliberately matching the mobile Room schema rather than `TIMESTAMPTZ`.
- `server_synced` (BOOLEAN, default FALSE): Set by `SyncRepository` once the outbox entry is accepted.

### `audit_logs`
System-wide activity tracking.
- `actor_id` (TEXT NOT NULL): Who performed the action — an `auth.users` id as text, or the literal `system`; `log_audit_event` defaults it to `'system'`. It is not a UUID column and carries no FK.
- `target_member_id` / `target_group_id` (UUID): The subject of the action.
- `related_id` (UUID) / `details` (JSONB): The row and payload the action touched.
- `action` (TEXT): Uppercase, free-form (no CHECK). The masters write `POLL_CREATED`, `VOTE_CAST`, `POLL_AUTO_CLOSED`, `DOCUMENT_VERIFIED`, `MEMBER_STATUS_CHANGED`, `LOAN_DISBURSED`, `LOAN_FORCED_SETTLEMENT`, `PAYOUT_COMPLETED`, `ROSCA_PAYOUT_INITIATED`, `RECONCILIATION_RESOLVED`, `CHECKPOINT_CREATED`.

### `platform_fee_config`
Pricing tiers for different group types.
- `PRIMARY KEY (group_type, rosca_cycle)`: Both columns are keys — `rosca_cycle` (INTEGER, default 0) is part of the identity, not a data column.
- `registration_fee` (NUMERIC): One-time setup fee.
- `monthly_fee` (NUMERIC): Ongoing maintenance fee.
- `shortfall_recovery_fee` (NUMERIC, default 5.00): Fee for processing under-contributions.

---

## ⚙️ Business Logic (Supabase RPCs)

### Error-code convention
Every financial RPC validates before it writes and raises a bracketed code, so a client never has to parse prose to decide what failed:

| Code | Meaning | Raised |
| :--- | :--- | :--- |
| `[FIN_001]` | insufficient liquidity for the requested outflow | after the balance read, before the debit |
| `[FIN_002]` | state precondition failed: bad loan/payout status, term not expired, group funds short | before any row is touched |
| `[FIN_003]` | input validation: non-positive amount, more than two decimal places, unsupported `status`/`type` literal | before any row is touched |
| `[SEC_001]` | `MFA_REQUIRED` — disbursement ≥ R10,000 needs an AAL2 session (`assert_mfa_for_disbursement_v1`) | before the outflow |
| `[SEC_002]` | caller not entitled: audit reports, platform-admin repairs, loan settlement | as the first statement |
| `[SYS_001]` | referenced group / member / loan row not found | at the lookup |
| `[SYS_002]` | a `transaction_id` was reused with different content — the idempotency guard | before the write |

Kotlin surfaces the human half of the message through `Throwable.toUserMessage()`; no call site keys on the bracket itself.

### Atomic Balance Helpers
- **`increment_group_balance`**: Updates `groups.balance` and inserts into `group_ledger`. `p_member_id` is optional (`DEFAULT NULL`) for non-member adjustments. Rejects a scale other than two decimals with `[FIN_003]`. It checks nothing about who is asking, so `SYNC_DELTA_20261029` withdraws `EXECUTE` from `PUBLIC`, `anon` **and** `authenticated`: only `service_role`, and only from inside a `SECURITY DEFINER` wrapper (a nested call is authorised against the wrapper's owner), may still call it by name.
- **`recompute_ledger_running_balances_v2`** / **`audit_and_fix_group_balance_v1`**: Set-based ledger rebuild and audit-and-repair helpers; prefer these over ad-hoc balance `UPDATE`s. `audit_and_fix_group_balance_v1` requires a platform admin (`[SEC_002] PLATFORM_ADMIN_ONLY`) and is not executable by `anon` or `PUBLIC`.
- **`consolidate_group_ledger_v1(UUID)`**: Rebuilds one group's `group_ledger` from the five source tables (contributions, repayments, payouts, loan disbursements, paid claims), books the platform-side money (`registration_contribution`, `member_fee`, `member_fee_ledger`) into `platform_ledger`, then delegates the running `balance_after` and `groups.balance` re-derivation to `recompute_ledger_running_balances_v2` — so a rebuild moves the cached column, which is the point of running it. The contract is that it and `internal_post_contribution` produce the same rows: same `status IN ('paid','partial')`, same `member_fee → platform_fee` category mapping, same `transaction_id` = the source row's id. It takes the group's `FOR UPDATE` lock plus the platform advisory lock, is idempotent (`ON CONFLICT DO NOTHING`, anti-join on `platform_ledger.transaction_id`), raises `[SYS_001] GROUP_NOT_FOUND` for an unknown group, and returns `entries_reconstructed` (rows it actually inserted), `platform_entries`, per-source counts, `final_balance` and `ledger_rows`. It does not repair an existing row that carries the wrong amount for a source id — `[SEED_003]` catches that. Rewritten by `SYNC_DELTA_20261101C`; behaviour is proved by `maintenance/CONSOLIDATE_REBUILD_PROBE.sql`, which rolls itself back.

### Inflow RPCs
- **`internal_post_contribution`** / **`record_contribution_v2`**: Record a payment, update the balance, append to the ledger, roll back as one unit. Both check amount sign and scale and reject an unknown status or type before writing, and both refuse a caller who is not the contributing member, that group's admin, a platform admin or `service_role` (`[SEC_002] CONTRIBUTION_ACCESS_DENIED`, `SYNC_DELTA_20261029`).
- **`record_platform_revenue_v2`**: Platform-revenue leg with advisory-lock idempotency, so a retried outbox entry cannot bill twice. Guarded with `[SEC_002] PLATFORM_REVENUE_ACCESS_DENIED`.
- **`pay_platform_fee_v2`**: Splits a member payment between platform revenue and group balance. Guarded with `[SEC_002] PLATFORM_FEE_ACCESS_DENIED`.

### Outflow RPCs
- **`approve_and_disburse_loan_v2`**: Validates solvency, moves the loan to `active`, and subtracts from balance. Requires `pending`/`approved` state (`[FIN_002]`), AAL2 above R10,000 (`[SEC_001]`), and a fail-closed liquidity read (`COALESCE(v_group.balance, 0)` → `[FIN_001]`). Guarded with `[SEC_002] LOAN_DISBURSEMENT_ACCESS_DENIED`, and `reviewed_by` now records the session rather than the `p_admin_id` the caller typed.
- **`complete_payout_v2`**: Completes a payout from `group_approved`/`processing` with the same MFA and fail-closed balance guards. Guarded with `[SEC_002] PAYOUT_COMPLETION_ACCESS_DENIED`; `processed_by` likewise takes the session identity.
- **`pay_burial_claim_v1`**: Pays a `beneficiary_payout_claims` row — the entry point `:data` had called by name since the claim flow shipped, with nothing behind it. Locks `groups` first, then the same chain as every other outflow: `[SEC_002] CLAIM_PAYMENT_ACCESS_DENIED`, payable from `submitted`/`under_review`/`approved` only (`[FIN_002] BAD_STATUS` — `paid`/`rejected`/`cancelled` are terminal, so a replayed call cannot draw the cover twice), `[FIN_003] INVALID_AMOUNT`, `[SEC_001]` MFA above R10,000, `[FIN_001] NO_CASH`. It posts the ledger row under category `claim`, description `'Burial claim: ' || beneficiary_name`, `transaction_id` = the claim id — the same row `consolidate_group_ledger_v1` reconstructs, so a live write and a ledger rebuild cannot diverge. `reviewed_by` takes `auth.uid()`, or a real typed `p_admin_id`; the nil UUID the platform console sends is dropped, because it is not a row in `auth.users` and `reviewed_by` has a foreign key there. Declared by `SYNC_DELTA_20261029B`.
- **`record_disbursement_v1`**: Books a named, categorised outflow and returns the new balance. It is a guard plus a sign check around `increment_group_balance` and nothing else — the primitive keeps ownership of the `FOR UPDATE` lock, the `[SYS_002]` idempotency key and the insufficient-funds check, so no rule exists in two places. It exists because `SYNC_DELTA_20261029` withdrew `increment_group_balance` from `authenticated`: a `SECURITY DEFINER` wrapper is authorised against its owner role, so this is the one route by which a signed-in group admin may still book a disbursement. Refusals: `[SEC_002] DISBURSEMENT_ACCESS_DENIED`, `[FIN_003] INVALID_AMOUNT` (positive, at most two decimals), `INVALID_DESCRIPTION`, `INVALID_CATEGORY`. Declared by `SYNC_DELTA_20261029B`.
- **`process_complex_payment_v4`**: Multi-leg payment orchestration under `FOR UPDATE`. Guarded with `[SEC_002] COMPLEX_PAYMENT_ACCESS_DENIED`, and the deploy script no longer grants it to `anon`.
- **`process_rosca_payout_v2` / `_v3`**: ROSCA pot disbursement with loan offsets; `_v3` carries the `[FIN_003]` amount checks. Both are guarded with `[SEC_002] ROSCA_PAYOUT_ACCESS_DENIED`; `_v2` has no period key, which is why it stays installed but guarded rather than dropped.
- **`record_loan_repayment_v1`**: Applies a repayment and syncs loan totals. Guarded with `[SEC_002] LOAN_REPAYMENT_ACCESS_DENIED`, because the loan-ownership check below it proves the loan belongs to `p_member_id` but `p_member_id` is whatever the caller sent.
- **`settle_expired_loan_v2(UUID)`**: Closes out an expired/defaulted loan in one transaction. New on both tiers via `SYNC_DELTA_20261028`; refuses a caller who is neither a platform admin nor that group's admin (`[SEC_002] LOAN_SETTLEMENT_ACCESS_DENIED`) and rejects a loan that is not in a settleable state.
- **`record_loan_settlement_extraction_v1`**: **Retired.** Its caller-supplied member, group and amount were never validated as one atomic settlement, so `EXECUTE` is revoked from `PUBLIC`, `anon`, `authenticated` and `service_role`. Use `settle_expired_loan_v2`.
- **`reconcile_bank_transaction_v1`**: Matches a bank line to internal ledger state. Guarded with `[SEC_002] BANK_RECONCILE_ACCESS_DENIED`, and a missing row now raises the coded `[SYS_001] BANK_TRANSACTION_NOT_FOUND` so `Throwable.toUserMessage()` can render it.
- **`automate_monthly_billing_v1`**: The scheduler's billing sweep. `EXECUTE` is `service_role` only; inside the function the guard is `IF auth.jwt() IS NOT NULL AND ...`, so a `pg_cron` run — which carries no JWT at all — is never asked who it is, while a request that does carry one must be a platform admin (`[SEC_002] MONTHLY_BILLING_ACCESS_DENIED`).
- **`is_own_member(UUID)`**: `STABLE` predicate the member-facing guards call — true when `members.user_id = auth.uid()`. New in `SYNC_DELTA_20261029`; not an endpoint and revoked from `anon`.

### Audit RPCs
`generate_platform_audit_report_v1`, `generate_group_audit_report_v1`, `generate_member_audit_report_v1` and `audit_and_fix_group_balance_v1` are `SECURITY DEFINER`, so RLS does not limit what they read. Each carries its own identity check (`[SEC_002]`) and the blanket `anon` grant is withdrawn in `03_SECURITY_AND_RLS.sql`. The platform report reads `groups` and `member_behavior_track` in a single pass each.

### Applied Sync Deltas
- Applied on **Local**: `20261014`, `20261021`, `20261022`, `20261026`, `20261026B`, `20261027`, `20261028`, `20261029`, `20261029B`, `20261030`, `20261030B`, `20261031`, `20261031B`, and `20261101` (a no-op there — 0 groups matched). Local was re-measured on 2026-10-29 and did not match its record — `20261029` and `20261029B` were absent (all eight ledger primitives still open to `anon`/`authenticated`, no `[SEC_002]` in any money body) while later deltas were present — and both were then applied in order, followed by `20261031`'s re-run on the current tier (`P.0.c` `false/false/true`, `[FIN_003]` measured with a real group admin).
- **Cloud, measured read-only on 2026-10-30**, was current through `20261030B`: the audit-report body is at the `20261030B` POST hash, `vw_ledger_integrity_check` is canonical with `anon` closed, `increment_group_balance` is not executable by `anon`, and `close_poll_v1`, `pay_burial_claim_v1` and `record_disbursement_v1` all exist. `20261031` was then applied there the same day (P.0 MISSING → P.1 → P.2 → P.3: body hash `f01eff7792049d78589f87453a46e38e`/1931, `anon_execute=false`, `authed_execute=true`, `svc_execute=true`, and both probes refusing with `[SEC_002]` and `[FIN_003]`), and `20261101` followed (7 ledger rows backfilled, 49/49 CONSISTENT, `cached_total` unchanged). Both tiers were then **rebuilt at ground zero and reseeded through the integrity gate on 2026-10-31**, which supersedes `20261101`/`20261101B` as data repairs: Local and Cloud read 19 groups / 1,397 ledger rows / R702,500.00 / 122 `platform_ledger` rows / R21,100.00 / drift 0. `20261101C` replaced the body of `consolidate_group_ledger_v1` and is the only delta in the series that was ever live on one tier alone: it ran on Local on 2026-10-31 and was applied to Cloud later the same day, so the two tiers briefly measured different digests over identical object counts before closing at `8405ef938e6325bf03b6002e54f5ecca`. Still run each delta's `P.0` pre-flight, in order, before applying anything there.
- `20261029` adds `[SEC_002]` caller-identity guards on the money and console RPCs, `is_own_member(UUID)`, withdraws `anon` execute from the money surface, adds `behavior_score` to `search_members_v1`, and adds coded `[SYS_001]` handling to `reconcile_bank_transaction_v1`. `20261029B` declares `close_poll_v1`, `pay_burial_claim_v1`, and `record_disbursement_v1`. `20261030` restores the canonical `vw_ledger_integrity_check` view and restricts its SELECT access to `authenticated`.
- **Cut; `20261030B`, `20261031`, `20261031B` and `20261101` are all applied on both tiers**: `20261030B` replaces `generate_group_audit_report_v1`'s four per-status member scans and two per-sign ledger scans with one aggregate scan per table (report shape, authorization and persisted snapshot unchanged). `20261031` adds `record_liquidity_v1`, the guarded inbound twin of `record_disbursement_v1`: 20261029 withdrew `increment_group_balance` from `authenticated`, which left no admin-callable way to book money that is not a contribution, and `record_liquidity_v1` restores one without going through `record_contribution_v2` (which would move `totalContributions` and therefore the investment-club unit price). `20261031B` re-revokes `anon` on `vw_ledger_integrity_check` for a tier where the old blanket `GRANT … ON ALL TABLES` in `03_SECURITY_AND_RLS.sql` was reapplied after the view's own `REVOKE`; that grant is now scoped to `relkind IN ('r','p')`, so a re-apply can no longer reopen a view, and this delta is the one-off repair. It has run on Local (2026-10-30); Cloud's `anon` grant was measured closed on 2026-10-30, so it needs no repair. All three carry read-only pre-flights; `20261030B` and `20261031` pin body fingerprints, `20261031B` pins the view hash and the ACL. `20261101` is the only data-only delta: on Cloud it turned 7 `DRIFT` groups (R385,500.00, all with a cached balance and zero ledger rows on a tier whose `group_ledger` was entirely empty) into CONSISTENT by inserting one `opening_balance` row per group (`amount = balance_after = groups.balance`, `transaction_id = 'opening_balance_<group_id>'`, `created_at` = the group's creation date); it changes no `groups.balance`, writes no contribution, and inserts nothing on a tier without offenders (Local). Its P.0 pins the offender list and the totals, and P.0.c proves `group_ledger` carries no trigger that could move a balance as a side effect.
- `SYNC_DELTA_20261022` repaired NULL cached balances from the ledger, set `groups.balance` to `NOT NULL`, and replaced both spend guards with `COALESCE(v_group.balance, 0)` checks that raise on insufficient funds.
- `SYNC_DELTA_20261030` restored the missing ledger-integrity view on Local. Its read-only verification found 11 of 49 groups with cached balances above their ledger sums; no balances were changed. Cloud was measured on 2026-10-30: the view is canonical there and reports 7 of 49 groups drifting, R385,500.00 in total, every one a fixture with an empty ledger.
- `SYNC_DELTA_20261026` pinned `search_path` on `increment_group_balance` and `pay_platform_fee_v2`; `20261026B` added the four claim evidence-status columns; `20261027` guarded the audit RPCs and withdrew `anon`/`PUBLIC` execute from them; `20261028` added `[FIN_003]` validation to the money path, created `settle_expired_loan_v2` and retired `record_loan_settlement_extraction_v1`.
- Deltas are append-only history: never rewrite an applied one; add a new dated file with bumped expected fingerprints.
- Canonical RPC source lives in `02_FUNCTIONS_AND_TRIGGERS.sql`; the generated masters (`migrations/00000000000000_GROUND_ZERO.sql`, `CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql`, `online/000_CONSOLIDATED_ONLINE_SYNC.sql`) are regenerated, never hand-edited.
- Tier drift is measured with `maintenance/SCHEMA_PARITY_DIGEST.sql` (object counts plus a digest hash) and `maintenance/SCHEMA_OBJECT_FINGERPRINTS.sql` (per-function body hashes). After a function body changes, reload PostgREST with `SELECT pg_notify('pgrst','reload schema');`.

---

## 🔐 Security

- **Row Level Security (RLS)**: Enforced on all tables based on `group_id` and `user_id`.
- **Function Hardening**: Every `SECURITY DEFINER` function pins `search_path`; a new RPC without a pin is a review blocker.
- **Anonymous reachability**: `03_SECURITY_AND_RLS.sql` grants `EXECUTE` to every role in bulk and then revokes it from `PUBLIC, anon` for the audit RPCs and `settle_expired_loan_v2`. Revoking only `anon` is not enough — PostgreSQL also grants `EXECUTE` to `PUBLIC` at creation time.
- **Caller identity on the money path**: `internal_post_contribution`, `record_contribution_v2`, `record_platform_revenue_v2`, `process_rosca_payout_v3`, `approve_and_disburse_loan_v2` and `complete_payout_v2` each open with `auth.role() IS DISTINCT FROM 'service_role' AND NOT (is_platform_admin() OR is_group_admin(…) OR is_own_member(…))` and raise a coded `[SEC_002] *_ACCESS_DENIED`; `SYNC_DELTA_20261029` added those guards and withdrew anonymous execute across the surface, and `is_own_member(UUID)` is what stops a member paying their own contribution being refused two frames into a nested call. What is still true: `[SEC_001] MFA_REQUIRED` fires only when `ABS(amount) >= 10000.00` (`assert_mfa_for_disbursement_v1`), so a sub-R10,000 disbursement by an authorised group admin needs only a valid loan id in `pending`/`approved` and a liquid group.
- **Verifying a revoke on Local**: prove it with `has_function_privilege('anon', 'fn(...)', 'EXECUTE')`. Do **not** probe an `anon`-role execution of these `SECURITY DEFINER` RPCs: on the Local stack it crashes Postgres 17.6.1 (signal 11) through both SQL and the PostgREST route. The `curl` leg of that check is cloud-only.
- **Financial Integrity**: `Double` is forbidden for currency. All math is performed using `NUMERIC` in Postgres and `Money` (BigDecimal) in Kotlin.
