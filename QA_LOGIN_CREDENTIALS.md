# QA Login Credentials — SanibonaniSave Master Seed

This document contains the login credentials for all high-fidelity personas and groups within the Master Seed data. These accounts are available in both **Local** and **Online** environments.

## 🔐 Global Credentials
- **Password (All Accounts)**: `Pass123!`
- **Encryption Tier**: Authoritative (AAL1/AAL2 enabled)

---

## 🏛️ Platform Administrator
| Role | Email | Purpose |
| :--- | :--- | :--- |
| Platform Admin | `torrymsimango@gmail.com` | Global oversight, fee management, backups, and support mode. |

---

## 👥 Core Groups (TIER 1)
All groups below have **1 Admin** (Torry) and **3 Persona Members**.

| Group Name | Member Email | Role |
| :--- | :--- | :--- |
| **LIGHT-Burial Society** | `persona.1.[1-3]@master.example.com` | Member / Dependent Testing |
| **LIGHT-Stokvel** | `persona.2.[1-3]@master.example.com` | Standard Savings |
| **LIGHT-Rosca** | `persona.3.[1-3]@master.example.com` | Peer Rotation & Pot Logic |
| **LIGHT-Investment Club** | `persona.4.[1-3]@master.example.com` | NAV & Unit Valuation |
| **LIGHT-Emergency Fund** | `persona.5.[1-3]@master.example.com` | Rapid Liquidity & Withdrawal Caps |
| **LIGHT-Community Savings** | `persona.6.[1-3]@master.example.com` | Traditional Burial/Savings Mix |
| **LIGHT-Tontine** | `persona.7.[1-3]@master.example.com` | Longevity Pool Testing |
| **LIGHT-Other** | `persona.8.[1-3]@master.example.com` | General Purpose |

> [!TIP]
> Use `persona.1.1@master.example.com` to test the **Burial Society** dashboard as a regular member.

---

## 🏆 Advanced Scenarios (TIER 2)
| Group Name | Member Email | Role | Purpose |
| :--- | :--- | :--- | :--- |
| **SCENARIO-Perfect Stokvel** | `perf.[1-5]@scen.com` | Investor | 100% On-time payment history over 12 months. |

---

## 🛠️ Usage Instructions

### 1. Connection Mode
- To test **Local**: Ensure `adb reverse tcp:45421 tcp:45421` is running and select **LOCAL** in Debug Settings.
- To test **Online**: Select **ONLINE** in Debug Settings.

### 2. Impersonation (Admin Only)
Logged in as `torrymsimango@gmail.com`, you can use the **Maintenance -> Support Tools** section to impersonate any of these members without knowing their specific passwords.

### 3. Verification
Run the `TOTAL_INTEGRITY_AUDIT.sql` script on your database to ensure all balances and ledger entries for these accounts are in sync.
