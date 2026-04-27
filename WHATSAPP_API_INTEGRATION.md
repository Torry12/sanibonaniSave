# WhatsApp Business API Integration

This document describes the implementation of the WhatsApp Business API integration for automated and manual notifications within the SanibonaniSave platform.

## 1. Overview
The integration allows the app to send WhatsApp messages directly to members using the Meta Graph API. This is used for payment reminders, fee enforcement alerts, and general group notifications.

## 2. Technical Implementation

### Data Models (`WhatsAppModels.kt`)
Created a comprehensive set of Kotlin Serialization models to map to the Meta Business API:
- `WhatsAppMessageRequest`: The top-level payload structure.
- `WhatsAppTemplate`: Support for Meta-approved message templates.
- `WhatsAppResponse`: Handles success and error responses from the API.

### API Service (`WhatsAppApiService.kt`)
A Retrofit interface defining the endpoint for sending messages:
- `POST /{phone_number_id}/messages`
- Uses Bearer Token authentication via `WHATSAPP_TOKEN`.

### Repository Layer (`Repositories.kt`)
Enhanced the `NotificationRepositoryImpl` with delivery logic:
- **`sendNotification`**: Now checks the `NotifChannel`. If set to `WHATSAPP` or `BOTH`, it triggers the API call.
- **`sendWhatsAppDirect`**: A private helper that cleans phone numbers (removes non-digits) and constructs the template parameters.
- **Dynamic Recipient Fetching**: Automatically resolves `member_id` to a phone number before sending.

### Dependency Injection
- **`NetworkModule.kt`**: Added a provider for `WhatsAppApiService` pointing to `https://graph.facebook.com/v21.0/`.
- **`AppModule.kt`**: Updated `provideNotificationRepository` to inject the `WhatsAppApiService`.

## 3. Configuration (Secrets)
The integration relies on the following `local.properties` keys:
- `WHATSAPP_TOKEN`: Permanent access token from Meta Business Manager.
- `WHATSAPP_PHONE_NUMBER_ID`: The unique ID of the sending phone number.

These are injected into the app via `BuildConfig`.

## 4. Usage Patterns

### Automated Fee Enforcement
When the `FeeEnforcementWorker` or `NotificationRepository` triggers a fee-related event:
1. The system identifies the Group Admin.
2. A WhatsApp message is sent notifying them of `DUE`, `WARNING`, or `SUSPENDED` status.

### Custom Notifications
```kotlin
notificationRepository.sendNotification(
    AppNotification(
        groupId = groupId,
        memberId = memberId,
        message = "Your contribution for June is due.",
        channel = NotifChannel.WHATSAPP,
        triggerEvent = NotifEvent.PAYMENT_DUE
    )
)
```

## 5. Requirements for Full Implementation
To move from the current partial implementation to a fully functional, production-ready system, the following steps are required:

1.  **Approved Meta Business Account**: You must have a verified Meta Business Manager account.
2.  **WhatsApp Official Templates**:
    *   Direct "Free-form" text is restricted to a 24-hour window after a user contacts you.
    *   For proactive notifications (reminders, alerts), you **must** register and get approval for templates (e.g., `general_notification`, `payment_reminder`) in the Meta Events Manager.
3.  **Permanent Access Token**: Generate a "System User" permanent token in Meta settings to prevent service interruption from expiring developer tokens.
4.  **Webhooks Implementation**:
    *   Implement a Supabase Edge Function to receive webhooks from Meta.
    *   This is required to track **Delivery Status** (Sent, Delivered, Read) and handle **User Replies**.

## 6. Document Download Feature
The platform now supports PDF document downloading for group members.
- **`FileDownloader`**: A utility using Android's `DownloadManager` to handle background downloads, progress notifications, and local storage.
- **UI Integration**: The `MemberDocumentsTab` now features a "Download" button for verified documents (SA ID, Proof of Residence), allowing members to keep offline copies of their group-verified files.

---
*Date: June 2024*
*Version: 1.1.0*
