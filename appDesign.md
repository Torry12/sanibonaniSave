# SanibonaniSave App Design & Functionality

## 1. Introduction
SanibonaniSave is a specialized financial platform tailored for South African community savings groups, such as Burial Societies, Stokvels, and ROSCAs. It combines traditional community trust with modern actuarial science and institutional investment access.

## 2. Core Functionalities

### 2.1 User Authentication & Profile Management
- **Secure Onboarding**: Sign-up and login powered by Supabase Auth with JWT persistence.
- **Role-Based Access**: Distinct experiences for Platform Admins, Group Admins, and Members.
- **Profile Customization**: Users can manage personal details, contact information, and upload profile photos.
- **Document Management**: A 5-slot document storage system (e.g., ID, Marriage Certificate, Group Constitution) with verification workflows.

### 2.2 Group Administration (The "Stokvel" Engine)
- **Group Creation**: Admins can establish new groups by defining:
    - Joining Fees & Monthly Contributions.
    - Maximum Member limits.
    - Rules for partial payments and grace periods.
    - Actuarial settings (e.g., Beneficiary increase percentages).
- **Member Management**: Admins monitor member statuses (Active, Probation, Suspended) and verify uploaded documents.
- **Financial Oversight**: Real-time tracking of group reserves, total contributions, and payout readiness.

### 2.3 Member Participation & Contributions
- **Discovery**: Map-based and list-based browsing of public groups using Geoapify for location services.
- **Joining Flow**: Seamless transition from group discovery to joining-fee payment and status activation.
- **Flexible Contributions**: Supports both full and partial payments (based on group rules) for monthly premiums.
- **Payment Integration**: Secure transaction processing via the YoCo payment gateway.
- **Personal Dashboard**: Members track their own contribution history, policy status, and upcoming deadlines.

### 2.4 Actuarial Science & Viability Planning
- **Viability Simulator**: A tool for admins to calculate initial vs. monthly contribution requirements based on target goals and timeframes.
- **Risk Scoring**: Composite risk scores and sustainability projections to ensure the long-term health of the burial society.
- **Analytics**: Advanced reporting on Reserve Adequacy, Loss Ratios, and Solvency Margins.

### 2.5 Communication & Notifications
- **Two-Way Messaging**: Members can send inquiries to admins, and admins can broadcast messages to the entire group.
- **System Alerts**: Automated notifications for low funding levels (< 70%), payment reminders, and status changes.
- **Multi-Channel Delivery**: In-app notifications supported by FCM (Firebase Cloud Messaging).

### 2.6 Data Portability & Reporting
- **CSV Export**: Admins and members can export contribution histories and member lists to CSV for offline record-keeping.
- **PDF Generation**: Integration with Supabase Edge Functions to generate professional PDF reports and certificates.

## 3. Technical Design Principles
- **Offline-First**: Uses Room DB as a local cache to ensure the app remains functional in areas with intermittent connectivity.
- **Real-Time Sync**: Leverages Supabase Realtime (Postgres Changes) to keep data consistent across all devices instantly.
- **Resilient Networking**: Implements exponential backoff for critical network operations like file uploads and database syncs.
- **Clean Architecture**: Follows MVVM with a robust Repository layer to separate concerns and improve testability.

## 4. About the Creator
**SanibonaniSave** was created and developed by **Torry Msimango**. The application represents a vision to bridge the gap between traditional community-based financial systems and modern financial technology, ensuring that no community is left behind in the digital economy.

## 5. User Documentation: How to Use SanibonaniSave

### 5.1 For Group Administrators
1.  **Register a Group**: From the landing screen, select "Register a Group". Complete the 5-step process including group details, location, and financial rules.
2.  **Payment**: Pay the one-time platform registration fee (R700) via YoCo to activate your group.
3.  **Manage Members**: Access the "Members" tab in your dashboard to view applications, verify documents, and monitor payment statuses.
4.  **Broadcast Messages**: Use the "Messaging" tab to send updates or reminders to all active members.
5.  **Financial Planning**: Use the "Viability" tab to simulate different contribution models and ensure your society's long-term sustainability.

### 5.2 For Members
1.  **Find a Group**: Browse the "Public Groups" on the map or in the list view.
2.  **Join a Group**: Select a group and tap "Join". You will be prompted to pay the group's joining fee.
3.  **Upload Documents**: Go to your Profile and upload the required documents (ID, etc.) for verification.
4.  **Make Contributions**: From your dashboard, tap "Pay Premium" to contribute your monthly amount. You can pay the full amount or a partial amount if the group allows it.
5.  **Track Progress**: Monitor your status (Active/Probation) and view your entire payment history in the "Contributions" tab.
6.  **Communicate**: Use the "Inquiries" section to message your group administrator directly.

### 5.3 General Navigation
-   **Dashboard**: Your central hub for all actions.
-   **Profile Icon**: Located in the top-right header, takes you directly to your profile and account settings.
-   **Bottom Navigation**: Switch between Dashboard, Messaging, Members (Admin), and Analytics.
