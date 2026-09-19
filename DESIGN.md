# SanibonaniSave — Design Specification

This document details the visual identity, UI architecture, and design principles of the SanibonaniSave platform.

---

## 🎨 1. Visual Identity

### Brand Essence
SanibonaniSave is built on **trust, transparency, and professional cooperative finance**. The design balances South African communal warmth with high-integrity financial utility.

### Color Palette
The app uses a sophisticated "Midnight Navy" base with "Teal" accents to represent stability and modern fintech.

| Category | Name | HEX | Usage |
|----------|------|-----|-------|
| **Brand** | Navy | `#0A192F` | Primary Brand Color (Deep) |
| **Brand** | Accent Blue | `#64FFDA` | Primary Action/Link Accent |
| **Surface** | Cream | `#F8FAFC` | Light Mode Backgrounds |
| **Status** | Success | `#10B981` | Positive Financial Health |
| **Status** | Warning | `#F97316` | Action Required / Overdue |
| **Status** | Error | `#EF4444` | Critical Failures / Arrears |

### Typography
- **Primary Font**: `Outfit` (via Google Fonts)
- **Scale**: Material 3 Standard
- **Key Styles**:
    - `Display Medium`: Black weight for Branding and Hero sections.
    - `Headline Small`: Bold for section titles.
    - `Body Large`: Normal for financial data and descriptions.
    - `Label Small`: SemiBold for metadata and status badges.

---

## 🏗️ 2. UI Architecture

### Component System
The app follows a **Material 3 (M3)** design system, customized for the South African context (ZAR formatting, communal terminology).

- **`AdaptiveScaffold`**: A custom wrapper that dynamically switches between `NavigationBar` (mobile), `NavigationRail` (tablets), and `NavigationDrawer` (large screens).
- **`SoftLoadingOverlay`**: Non-blocking visual feedback for background data synchronization.
- **`StatCard`**: Standardized component for displaying financial metrics (Savings, Interest, Shortfall).
- **`GroupIcon`**: Emoji-based identity system for quick group recognition.

### Modular Layouts
UI is organized into **Feature-Based Packages**:
- `features/landing`: Guest experience and value proposition.
- `features/auth`: High-security login and registration flows.
- `features/member`: Personal savings portal and loan management.
- `features/admin`: Group-level administration, actuarial tools, and payouts.
- `features/financial`: Payment gateways and sandbox testing.

---

## 🔄 3. Key User Flows

### The "Bootloader" Sequence
Ensures the app is fully hydrated before entering critical financial screens.
```mermaid
graph TD
    A[Launch] --> B[Branding Splash]
    B --> C[Connection Establishment]
    C --> D[Account Hydration]
    D --> E[Context Warming]
    E --> F[Dashboard]
```

### Dashboard Experience
Both Member and Admin dashboards use a **Tabbed Horizontal Pager** system for deep exploration of group data without losing context.

**Member Tabs**:
1. Overview (Stats)
2. Savings (Ledger)
3. Loans (Repayments)
4. Beneficiaries (Burial)
5. Documents (KYC)

---

## ⚖️ 4. Design Principles

### 1. Financial Precision
- **No Floating Point**: All currency displays use `BigDecimal` via the `Money` domain model.
- **ZAR Standard**: Standardized formatting `R1,234.56` across all screens.

### 2. Transparency First
- **The Ledger View**: Every transaction is visible as an immutable ledger entry.
- **Audit Trails**: Actions in "Support Mode" are explicitly flagged with high-visibility banners to maintain member privacy trust.

### 3. Resilience & Offline-First
- **Offline Banners**: Clear notification when the app is in offline mode.
- **Status Persistence**: Using linear and circular progress indicators to show sync progress, ensuring the user knows their data is being safely reconciled.

---

## 📱 5. Adaptive Strategy
The app is optimized for:
- **Portrait Mobile**: Primary target; optimized for one-handed operation.
- **Low-End Devices**: Minimal use of heavy animations; efficient Room-to-Compose data flow.
- **Rural Connectivity**: High-visibility offline indicators and retry mechanisms.

---
**Last Updated**: August 2026
**Version**: 2.0 (Material 3 Adaptive)
