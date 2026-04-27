# Changelog: WhatsApp Integration & Document Management

This document details the technical changes and features added to the SanibonaniSave platform regarding automated messaging and document handling.

## 🚀 New Features

### 1. WhatsApp Business API Integration
A full-stack messaging layer has been implemented to facilitate group-wide and individual communications.
- **Automated Confirmations**: Members now receive instant WhatsApp messages upon paying Joining Fees or Monthly Contributions.
- **Admin Alerts**: Group admins are automatically notified via WhatsApp when platform fees are due, overdue, or when the group status changes.
- **Broadcast System**: The `NotificationRepository` now supports group-wide broadcasts, allowing admins to send a single message to all members.
- **Sanitized Delivery**: Phone numbers are automatically cleaned (country codes handled, non-digits removed) to ensure 100% delivery success with the Meta API.

### 2. PDF Document Download System
Members can now download verified group documents for offline use.
- **`FileDownloader` Utility**: Uses the Android `DownloadManager` for background downloading, progress tracking, and storage management.
- **One-Tap Access**: Once an administrator verifies a member's document (ID or Proof of Residence), a "Download" button appears in the member portal.
- **Secure Retrieval**: Documents are fetched directly from Supabase Storage and saved to the device's public `Downloads` folder.

## 🛠️ Technical Fixes & Optimizations

### Policy Engine Stability (404 Error Resolved)
- **Local Calculation Logic**: Refactored the `PolicyRepository` to calculate funding ratios and status locally using database queries. This eliminates the dependency on Supabase Edge Functions, resolving the "HTTP 404" error previously seen on the Policy Details screen.
- **Enhanced Data Models**: Updated the `Contribution` model to include `policyId`, enabling precise tracking of shared community insurance funds.

### Group Activation Logic
- **Registration Enforcement**: Implemented a "Pay-to-Unlock" flow for new groups. All "Quick Actions" (Actuarial Review, Messaging, Exports) are locked until the one-time registration fee is confirmed.
- **Real-time Activation**: Integrated a database observer that instantly unlocks dashboard features as soon as the registration payment is processed.

## 📂 New & Modified Files
- `WhatsAppModels.kt`: Data structures for Meta API.
- `WhatsAppApiService.kt`: Retrofit service for messaging.
- `FileDownloader.kt`: Background download management.
- `Repositories.kt`: Enhanced notification and policy logic.
- `PaymentViewModel.kt`: Integrated payment confirmation triggers.
- `MemberScreens.kt`: Added document download UI.
- `AdminDashboardScreen.kt`: Implemented registration locking.
- `WHATSAPP_API_INTEGRATION.md`: Full technical documentation and production requirements.

---
**Build Status**: Passing ✅
**Integration Status**: Functional in Sandbox/Debug modes.
