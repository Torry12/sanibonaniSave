# Logout Button Standardization

## Overview
The logout button has been standardized across the SanibonaniSave platform to provide consistent styling, behavior, and user experience.

## Changes Made

### 1. New Standardized `LogoutButton` Composable
**File**: `app/src/main/java/com/sanibonani/save/ui/components/SharedComponents.kt`

Added a new reusable `LogoutButton` composable with three style variants:

```kotlin
@Composable
fun LogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LogoutButtonStyle = LogoutButtonStyle.Filled,
    showIcon: Boolean = true
)

enum class LogoutButtonStyle {
    Filled,      // Full-width filled button with transparent container (primary action)
    Outlined,    // Full-width outlined button with red border (secondary action)
    MenuItem     // Dropdown menu item (used in profile menus)
}
```

#### Style Specifications:

- **Filled Style** (e.g., Platform Admin Screen):
  - Full-width button
  - Transparent container
  - Red text (`ErrorRed`)
  - Black font weight
  - 52dp height via `SanibonaniButton`

- **Outlined Style** (e.g., Admin Dashboard):
  - Full-width button
  - Red border (1dp)
  - Red text
  - 48dp height
  - Rounded corners (12dp)

- **MenuItem Style** (e.g., Profile dropdown):
  - Dropdown menu item
  - Red text with `ExitToApp` icon
  - Uses `DropdownMenuItem` composable
  - Optional icon toggle

### 2. Updated PlatformAdminScreen
**File**: `app/src/main/java/com/sanibonani/save/ui/screens/admin/PlatformAdminScreen.kt`

**Before**:
```kotlin
SanibonaniButton(
    text = "LOGOUT",
    onClick = onLogout,
    modifier = Modifier.fillMaxWidth(),
    containerColor = Color.Transparent,
    contentColor = ErrorRed
)
```

**After**:
```kotlin
LogoutButton(
    onClick = onLogout,
    style = LogoutButtonStyle.Filled
)
```

**Benefits**:
- Consistent text: "Log Out" (instead of "LOGOUT")
- Automatic margin/spacing handling
- Reusable and maintainable

### 3. Updated AdminDashboardScreen
**File**: `app/src/main/java/com/sanibonani/save/ui/screens/admin/AdminDashboardScreen.kt`

**Before**:
```kotlin
OutlinedButton(
    onClick = onLogout,
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
    border = BorderStroke(1.dp, ErrorRed)
) {
    Text("Log Out")
}
```

**After**:
```kotlin
LogoutButton(
    onClick = onLogout,
    style = LogoutButtonStyle.Outlined
)
```

**Benefits**:
- Consistent appearance with PlatformAdminScreen variant
- Reduced boilerplate code
- Easier to maintain styling across the app

### 4. Updated Dropdown Menu in DashboardHeaderWithNotif
**File**: `app/src/main/java/com/sanibonani\save\ui\components\SharedComponents.kt`

**Before**:
```kotlin
DropdownMenuItem(
    text = { Text("Log Out", color = ErrorRed) },
    onClick = {
        showMenu = false
        onLogoutClick()
    },
    leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = ErrorRed) }
)
```

**After**:
```kotlin
LogoutButton(
    onClick = {
        showMenu = false
        onLogoutClick()
    },
    style = LogoutButtonStyle.MenuItem,
    showIcon = true
)
```

**Benefits**:
- Consistent logout action handling
- Centralizes logout styling for dropdown context
- Easier to update styling globally

## Design Standards

### Color Palette
- Text: `ErrorRed` (#E53935)
- Border (when outlined): `ErrorRed` (#E53935)
- Container: `Transparent` (for filled style) or `White` (default)

### Typography
- Font Weight: `Bold` (for outlined) or `Black` (via SanibonaniButton for filled)
- Style: `labelLarge`
- Text: "Log Out" (consistent across all screens)

### Spacing
- Full-width buttons: `fillMaxWidth()` modifier
- Filled style: 52dp height (via SanibonaniButton)
- Outlined style: 48dp height
- Margins: Managed by individual screens

### Icons
- Icon: `Icons.Default.ExitToApp`
- Size: 18dp (outlined), included in MenuItem style
- Color: `ErrorRed`
- Togglable via `showIcon` parameter

## Usage Examples

### Filled Style (Primary Action)
```kotlin
LogoutButton(
    onClick = { logout() },
    style = LogoutButtonStyle.Filled
)
```

### Outlined Style (Secondary Action)
```kotlin
LogoutButton(
    onClick = { logout() },
    style = LogoutButtonStyle.Outlined
)
```

### Menu Item Style (In Dropdowns)
```kotlin
LogoutButton(
    onClick = { logout() },
    style = LogoutButtonStyle.MenuItem,
    showIcon = true
)
```

### Custom Modifier
```kotlin
LogoutButton(
    onClick = { logout() },
    style = LogoutButtonStyle.Outlined,
    modifier = Modifier.padding(16.dp)
)
```

## Benefits of Standardization

1. **Consistency**: All logout buttons follow the same design pattern
2. **Maintainability**: Changes to logout styling only need to be made in one place
3. **Code Reusability**: Reduces duplication across multiple screens
4. **User Experience**: Familiar logout interaction across the app
5. **Accessibility**: Consistent sizing and iconography
6. **Future-Proof**: Easy to extend with additional styles if needed

## Testing Recommendations

1. **Visual Testing**:
   - Verify logout button appearance on all screens (Platform Admin, Admin Dashboard, Profile menu)
   - Test all three style variants
   - Check button responsiveness on different screen sizes

2. **Functionality Testing**:
   - Confirm logout action works from all screens
   - Verify navigation to landing screen after logout
   - Check session clearing

3. **Accessibility Testing**:
   - Verify button is keyboard accessible
   - Check color contrast meets WCAG standards
   - Test with screen readers

## Files Modified

1. ✅ `app/src/main/java/com/sanibonani/save/ui/components/SharedComponents.kt` - Added LogoutButton composable
2. ✅ `app/src/main/java/com/sanibonani/save/ui/screens/admin/PlatformAdminScreen.kt` - Updated to use LogoutButton
3. ✅ `app/src/main/java/com/sanibonani/save/ui/screens/admin/AdminDashboardScreen.kt` - Updated to use LogoutButton
4. ✅ `app/src/main/java/com/sanibonani/save/ui/components/SharedComponents.kt` (DashboardHeaderWithNotif) - Updated dropdown to use LogoutButton

## No Breaking Changes

These changes are backward compatible and don't affect any existing APIs or data structures. All logout functionality remains the same, only the UI presentation has been standardized.

