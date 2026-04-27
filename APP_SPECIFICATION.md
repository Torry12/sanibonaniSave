# SanibonaSave App Specification

## 1. Overview
SanibonaSave is a community-based financial coordination app designed for low-income and irregular income earners in South Africa. It facilitates group-based financial responsibility, specifically focusing on policies (like funeral cover) with flexible contributions.

## 2. Core Architecture
- **Language**: Kotlin
- **Pattern**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt
- **Asynchronous Programming**: Coroutines + Flow
- **Network Layer**: 
    - Supabase SDK (PostgREST, Auth, Realtime, Storage, Functions)
    - Retrofit (for custom Supabase Edge Functions and external APIs like Geoapify)
- **Local Database**: Room (Offline-first approach)
- **UI Framework**: Jetpack Compose (Material 3)

## 3. Key Modules & Engines
### 3.1 Policy Engine
Handles the lifecycle and display of financial policies.
- **Data Models**: `Policy`, `PolicyStatusResponse`
- **Repository**: `PolicyRepository`
- **ViewModel**: `PolicyViewModel`
- **Features**: Policy creation, real-time funding status tracking, risk detection for drop-offs.

### 3.2 Contribution Engine
Manages the payment flow into policies.
- **Data Models**: `Contribution`, `ContributionRequest`, `Payment`
- **Repository**: `PaymentRepository`
- **Features**: Flexible contribution amounts, transaction history, integration with YoCo payment gateway.

### 3.3 Group Engine
Manages the "Stokvel" or community group logic.
- **Data Models**: `Group`, `Member`
- **Repository**: `GroupRepository`, `MemberRepository`
- **Features**: Group registration (with location services via Geoapify), member onboarding, group-level analytics.

### 3.4 Notification Engine
Handles communication with users.
- **Channels**: In-app notifications, FCM (Push), WhatsApp (planned), Email.
- **Triggers**: Low funding alerts (< 70%), payment reminders, group status changes.

### 3.5 Actuarial & Investment Engine
Provides advanced financial insights.
- **Features**: Risk scoring, solvency margins, loss ratios, and access to institutional investment options.

## 4. User Roles
- **Platform Admin**: Manages the entire platform, views global analytics, and handles payouts.
- **Group Admin**: Manages a specific Stokvel/Group, sets contribution rules, and monitors member status.
- **Member**: Contributes to policies, views personal progress, and receives alerts.

## 5. Navigation & UI
- **Landing**: Entry point for browsing groups or logging in.
- **Auth**: Secure login and registration (via Supabase Auth).
- **Browse**: Map-based and list-based discovery of public groups.
- **Dashboards**: Role-specific portals (Member vs. Admin) providing tailored insights and actions.
- **Payment**: Unified interface for all financial transactions via YoCo.

## 6. Technical Constraints
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35
- **Offline Support**: Local caching via Room for intermittent connectivity.
- **Low Data Usage**: Optimized network calls and lazy loading.
