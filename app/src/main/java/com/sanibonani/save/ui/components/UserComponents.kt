package com.sanibonani.save.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.ui.theme.*

@Composable
fun DashboardHeaderWithNotif(
    title: String,
    subtitle: String,
    notifCount: Int,
    onProfileClick: () -> Unit,
    onNotifClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchPortal: () -> Unit,
    profileImageUrl: String? = null,
    profileImageVersion: Long = 0L,
    isPortalSwitchable: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                ),
                fontWeight = FontWeight.Black
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onNotifClick) {
            BadgedBox(
                badge = {
                    if (notifCount > 0) {
                        Badge { Text(notifCount.toString()) }
                    }
                }
            ) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                if (profileImageUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(profileImageUrl)
                            .memoryCacheKey("$profileImageUrl-$profileImageVersion")
                            .build(),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("View Profile") },
                    onClick = {
                        showMenu = false
                        onProfileClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )
                if (isPortalSwitchable) {
                    DropdownMenuItem(
                        text = { Text("Switch to Admin") },
                        onClick = {
                            showMenu = false
                            onSwitchPortal()
                        },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }
                    )
                }
                HorizontalDivider()
                LogoutButton(
                    onLogout = {
                        showMenu = false
                        onLogoutClick()
                    },
                    style = LogoutButtonStyle.MenuItem
                )
            }
        }
    }
}

/**
 * Standard confirmation dialog presented before logging the user out.
 */
@Composable
fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Log Out",
    message: String = "Are you sure you want to log out of your account?"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Standardized Logout Button component supporting Filled, Outlined, MenuItem, and IconButton styles.
 * Includes built-in confirmation dialog flow for consistent security and UX.
 */
@Composable
fun LogoutButton(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    style: LogoutButtonStyle = LogoutButtonStyle.Outlined,
    isLoading: Boolean = false,
    label: String = "Log Out",
    confirmRequired: Boolean = true,
    confirmTitle: String = "Log Out",
    confirmMessage: String = "Are you sure you want to log out of your account?"
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    val handleLogoutRequest = {
        if (confirmRequired) {
            showConfirmDialog = true
        } else {
            onLogout()
        }
    }

    if (showConfirmDialog) {
        LogoutConfirmationDialog(
            title = confirmTitle,
            message = confirmMessage,
            onConfirm = {
                showConfirmDialog = false
                onLogout()
            },
            onDismiss = {
                showConfirmDialog = false
            }
        )
    }

    when (style) {
        LogoutButtonStyle.Filled -> {
            Button(
                onClick = handleLogoutRequest,
                modifier = modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
        LogoutButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = handleLogoutRequest,
                modifier = modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ErrorRed),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ErrorRed)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
        LogoutButtonStyle.MenuItem -> {
            DropdownMenuItem(
                text = { Text(label, color = ErrorRed, fontWeight = FontWeight.Bold) },
                onClick = handleLogoutRequest,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed) },
                enabled = !isLoading
            )
        }
        LogoutButtonStyle.IconButton -> {
            IconButton(
                onClick = handleLogoutRequest,
                modifier = modifier,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ErrorRed)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = label, tint = ErrorRed)
                }
            }
        }
    }
}
