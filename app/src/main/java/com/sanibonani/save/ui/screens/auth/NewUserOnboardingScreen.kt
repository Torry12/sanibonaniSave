package com.sanibonani.save.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.rememberClickDebouncer

/**
 * Shown immediately after a new user registers an account.
 * Presents two clear onboarding actions:
 *   1. Register a new savings group (admin path)
 *   2. Browse groups and join one (member path)
 *
 * The user's name is read from [UserProfileCacheService] for a personalised welcome.
 */
@Composable
fun NewUserOnboardingScreen(
    profileCache: UserProfileCacheService,
    onRegisterGroup: () -> Unit,
    onBrowseGroups: () -> Unit
) {
    val clickDebouncer = rememberClickDebouncer()
    val firstName = remember {
        profileCache.getFullName().trim().substringBefore(" ").ifBlank { "there" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Forest, ForestMid)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Welcome header ──────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Welcome, $firstName! 🎉",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your account is ready. What would you like to do next?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(48.dp))

            // ── Option 1 — Register a Group ────────────────────────────────────
            OnboardingOptionCard(
                icon           = Icons.Default.GroupAdd,
                iconTint       = Gold,
                title          = "Register a New Group",
                description    = "Start your own burial society, stokvel, or ROSCA. You'll be the group admin and can invite members.",
                actionLabel    = "Register Group",
                actionColor    = Gold,
                actionTextColor = Forest,
                onClick        = { clickDebouncer.processClick(onRegisterGroup) }
            )

            Spacer(Modifier.height(20.dp))

            // ── Divider ─────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.3f)
                )
                Text(
                    "  OR  ",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.3f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Option 2 — Browse & Join ────────────────────────────────────────
            OnboardingOptionCard(
                icon            = Icons.Default.Search,
                iconTint        = GoldLight,
                title           = "Browse & Join a Group",
                description     = "Find an existing group near you. View details, check membership requirements, and apply to join.",
                actionLabel     = "Browse Groups",
                actionColor     = Color.White,
                actionTextColor = Forest,
                onClick         = { clickDebouncer.processClick(onBrowseGroups) }
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text = "You can always do both later from the app home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OnboardingOptionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    actionLabel: String,
    actionColor: Color,
    actionTextColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionColor,
                    contentColor   = actionTextColor
                )
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

