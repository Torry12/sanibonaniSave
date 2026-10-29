# SKILLS.md — SanibonaniSave Developer & AI Agent Skills

**Last Updated:** August 31, 2026
**Purpose:** This document tracks the core development skills, architectural patterns, and AI agent capabilities leveraged to build and maintain the SanibonaniSave platform.

---

## 1. 📱 Core Android Development
* **Kotlin (2.4.21)**: Advanced language features including sealed classes for state management, reified inline functions, and extension properties.
* **Jetpack Compose (2026.09.00 BOM, Material 3)**: Declarative UI development, state hoisting, and custom modifiers.
* **Coroutines & Flow**: Advanced asynchronous programming, `StateFlow`/`SharedFlow` for reactive UI updates, and structured concurrency.
* **Dependency Injection**: Full project wiring using **Dagger Hilt 2.60.1** across a multi-module architecture (`:app`, `:data`, `:domain`, `:simulation`).
* **Clean Architecture**: Strict modularization enforcing boundary rules and domain-driven design (DDD).

## 2. 🎨 UI/UX & Theming (Jetpack Compose)
* **Glassmorphism & 3D Styling**: Advanced Compose `Brush` gradients, multi-layered translucency (`alpha`), specular highlights, and 3D bevel illusions.
* **Animated Content**: Implementation of `animateContentSize`, `AnimatedVisibility`, `Crossfade`, and `animateFloatAsState` for micro-interactions (e.g., button presses).
* **Adaptive Layouts**: Responsive design techniques using weights, safe areas, and dynamic grid layouts.

## 3. 💾 Data & Backend Architecture
* **Offline-First Resilience**: Implementation of the **Offline Outbox Pattern** using Room SQLite to queue mutations when offline and process them chronologically upon reconnection.
* **Parallel Data Hydration**: Multiplexed realtime connections (`RealtimeSyncManager`) and `Semaphore`-throttled concurrent Coroutines (`SyncRepository`) to rapidly hydrate the UI without overwhelming the network or device.
* **Supabase Integration**: Deep integration with Supabase Auth, Storage, Realtime, and PostgREST.
* **PostgreSQL RPCs**: Offloading heavy transactional operations (complex payments, loan disbursements) to secure, atomic `plpgsql` functions on the backend.

## 4. 🧮 Financial Engineering
* **Precision Mathematics**: Eradication of floating-point drift using a custom `Money` wrapper around `BigDecimal`.
* **Actuarial Logic**: Group-type specific accounting strategies (Burial Society, Stokvel, ROSCA, Investment Club) implementing the Strategy Pattern (`AccountingStrategy`).
* **Audit Trails & Ledgers**: Immutable, double-entry bookkeeping schemas linking to every mutation.

## 5. 🧪 Testing & Quality Assurance
* **Coroutines Testing**: Utilization of `StandardTestDispatcher` and `UnconfinedTestDispatcher` for controlling virtual time and resolving race conditions.
* **Mocking & Verification**: Extensive use of **MockK** for verifying UseCases, mocking Repository layers, and simulating external systems (e.g., `Log`, `FirebaseCrashlytics`).
* **Live Simulations**: Automated "time-travel" testing (`LiveSimulationRunner`) simulating months of member behavior for QA.

## 6. 🤖 Agentic AI & Extensibility
* **AI Feature Tracking**: Exporting structured behavioral tracking logs (`MemberBehaviorTrack`) into SFT/DPO formats for future AI model finetuning on Azure AI Foundry.
* **Automated Code Discovery**: Leveraging Semantic Search, grep, and structural outline parsing to navigate, understand, and safely modify legacy architectures.
* **Surgical Replacements**: Using strict concurrency-safe patching tools to alter multi-line Compose UI components or ViewModel logic blocks.
