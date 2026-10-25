# Android Studio AI Prompt – Integrate ROSCA Features into Existing SanibonaniSave Application

You are working on an existing Android application called **SanibonaniSave**. Do **not** create a new project or replace the current architecture. Instead, analyze the existing codebase and incrementally integrate the features described below while preserving compatibility with the current implementation.

## Project Goal

SanibonaniSave is a South African digital platform for managing savings groups (stokvels, ROSCAs, burial societies, investment clubs, and community savings associations). The objective is to transform the existing application into a scalable, enterprise-grade financial platform while maintaining backward compatibility.

The AI should first inspect the existing project structure, identify reusable components, and integrate new functionality with minimal disruption. Refactor only where necessary to improve maintainability or support new capabilities.

---

# Development Principles

Before writing code:

* Analyze the current project architecture.
* Reuse existing ViewModels, repositories, database tables, navigation graphs, and UI components where appropriate.
* Avoid duplicate functionality.
* Preserve existing APIs unless improvements are necessary.
* Use incremental refactoring rather than large rewrites.
* Generate migration scripts for any database schema changes.
* Ensure all new functionality is covered by unit and integration tests.

---

# Existing Architecture

Maintain and extend the existing architecture using:

* Kotlin
* Jetpack Compose
* MVVM
* Clean Architecture
* Hilt
* Room
* Supabase
* Kotlin Coroutines
* Flow / StateFlow
* Material Design 3

If existing modules differ from these patterns, adapt the implementation rather than forcing a complete redesign.

---

# Expanded Business Scope

The application must support multiple types of community financial organizations, including:

* Traditional ROSCAs (rotating payouts)
* Accumulating savings clubs
* Burial societies
* Investment clubs
* Grocery clubs
* Loan societies
* Hybrid community savings groups

Each organization may define its own constitution and operating rules.

---

# Group Module Enhancements

Extend the existing Group module to support:

* Group constitutions
* Custom contribution rules
* Multiple contribution frequencies
* Variable contribution categories
* Member eligibility rules
* Voting rules
* Distribution methods
* Group documents
* Meeting schedules
* Multiple administrators
* Committee members
* Beneficiary management

Each group should remain logically isolated from all other groups.

---

# Member Module Enhancements

Extend the existing Member functionality with:

* Member profile photos
* National ID verification
* Beneficiary information
* Banking details
* Emergency contacts
* Membership status history
* Join and exit dates
* Role history
* Digital membership cards
* Document uploads
* Member notes

Do not remove any existing member functionality.

---

# Financial Ledger

Introduce an immutable accounting system.

Every financial event must create permanent ledger entries.

Supported transaction types include:

* Contributions
* Payouts
* Loan disbursements
* Loan repayments
* Interest
* Penalties
* Donations
* Investments
* Refunds
* Expenses
* Bank charges

Transactions must never be edited or deleted.

Corrections must be performed through reversal entries.

Every transaction must be traceable.

---

# Contribution Engine

Extend contribution processing to support:

* Monthly contributions
* Weekly contributions
* Daily contributions
* Ad-hoc contributions
* Special collections
* Funeral levies
* Investment contributions
* Donation campaigns

Support:

* Partial payments
* Overpayments
* Arrears
* Advance payments
* Automatic balance calculations

---

# Distribution Engine

Support multiple payout methods:

* Traditional rotating payouts
* Scheduled distributions
* Emergency payouts
* Funeral claims
* Investment withdrawals
* Profit sharing
* Dividend distributions

Validate eligibility before every payout.

---

# Loans Module

Add optional loan functionality.

Features:

* Loan applications
* Loan approvals
* Guarantors
* Repayment schedules
* Interest calculations
* Penalties
* Early settlement
* Outstanding balances

---

# Meeting Management

Add:

* Meeting scheduling
* Attendance tracking
* Digital minutes
* Voting
* Resolution tracking
* Action items

---

# Document Management

Support uploading and organizing:

* Constitutions
* Member IDs
* Receipts
* Bank statements
* Minutes
* Contracts
* Insurance documents
* Beneficiary forms

Store documents securely using Supabase Storage.

---

# Notifications

Extend notifications to support:

* Push notifications
* WhatsApp
* SMS
* Email

Notify members for:

* Contribution reminders
* Payment confirmations
* Missed payments
* Upcoming meetings
* Loan repayments
* Payouts
* Announcements
* Emergency notices

---

# Reporting

Extend reporting with:

Financial reports:

* Contribution summaries
* Cash flow
* Trial balance
* Balance sheet
* Income statement

Operational reports:

* Active members
* Arrears
* Attendance
* Distribution history
* Loan performance

Executive dashboards:

* Collection rates
* Member growth
* Liquidity
* Group health
* Forecasts

---

# Security

Implement:

* Role-Based Access Control (RBAC)
* Audit logging
* Secure authentication
* Encryption of sensitive information
* Secure API communication
* Session management

Financial operations should require appropriate authorization.

---

# Audit System

Record every important event:

* User
* Timestamp
* Previous value
* New value
* Device
* IP address (where available)
* Operation performed

Audit records must be immutable.

---

# Offline Support

Extend existing offline capabilities by:

* Queueing financial transactions
* Conflict resolution
* Automatic synchronization
* Retry mechanisms
* Local caching

---

# Scalability

The architecture should support:

* Thousands of independent groups
* Hundreds of thousands of members
* Millions of financial transactions
* Future web and iOS clients
* Microservice migration without major redesign

---

# AI and Future Expansion

Design extension points for future features, including:

* AI-powered financial insights
* Spending and savings recommendations
* Fraud detection
* Predictive cash-flow forecasting
* Automated reconciliation
* Natural language reporting
* Intelligent member support
* Integration with South African payment providers (e.g., Stitch, Ozow, PayFast, Peach Payments, Yoco) and banking APIs

---

# Coding Standards

When implementing features:

1. Analyze the existing code before modifying it.
2. Reuse existing components wherever practical.
3. Prefer extension over replacement.
4. Keep modules loosely coupled.
5. Follow SOLID principles.
6. Maintain clean architecture boundaries.
7. Write comprehensive unit and integration tests.
8. Document all schema changes and migrations.
9. Preserve backward compatibility with existing user data.
10. If a proposed change could break existing functionality, explain the impact and suggest a migration strategy before implementing it.
