# Impersonation Flows in SanibonaniSave

## Overview
Impersonation allows a platform admin to temporarily act as a group admin or member for troubleshooting, support, or audit purposes. This is a privileged operation and is strictly controlled and audited.

## Where Impersonation is Triggered
- **UI:** The Platform Admin dashboard (`PlatformAdminScreen.kt`) provides buttons to impersonate group admins or members. These actions are only visible to users with the `PLATFORM_ADMIN` role.
- **ViewModel:** The `PlatformAdminViewModel` manages impersonation state, including the selected group or member to impersonate, and updates the UI accordingly.

## How Impersonation Works
1. **Trigger:** Platform admin clicks an impersonate button for a group admin or member.
2. **State Update:** The ViewModel updates impersonation state (e.g., `impersonationGroupId`, `impersonationMembers`).
3. **Navigation:** The app navigates to the appropriate dashboard as if the admin were the impersonated user.
4. **Audit:** All impersonation actions are logged via `logAudit` for traceability.
5. **Revert:** The admin can exit impersonation and return to their own context at any time.

## Enforcement and Security
- **Frontend:** Only users with the `PLATFORM_ADMIN` role see impersonation options. The ViewModel ensures only valid impersonation targets are allowed.
- **Backend:** All data access is still subject to RLS (Row Level Security) and platform admin bypass policies. Impersonation does not grant access beyond what the platform admin already has.
- **Audit:** Every impersonation action is logged with details (who, when, target, action) for security review.

## Key Files
- `app/src/main/java/com/sanibonani/save/ui/screens/admin/PlatformAdminScreen.kt`
- `app/src/main/java/com/sanibonani/save/viewmodel/PlatformAdminViewModel.kt`
- `supabase/functions/mobile-admin-actions/index.ts` (for backend role enforcement)

## Security Considerations
- Impersonation is only available to platform admins.
- All actions taken during impersonation are attributed to the platform admin in audit logs.
- Backend RLS ensures no data is exposed beyond platform admin rights.

## Recommendations
- Maintain strict audit logging for all impersonation events.
- Regularly review impersonation logs for unusual activity.
- Ensure tests cover impersonation state and navigation flows.

