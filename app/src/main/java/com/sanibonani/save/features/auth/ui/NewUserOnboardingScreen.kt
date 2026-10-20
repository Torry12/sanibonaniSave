package com.sanibonani.save.features.auth.ui

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.features.auth.ui.GroupTypeChip
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.domain.model.GroupType

/**
 * Shown immediately after a new user registers an account.
 * Presents the user's primary onboarding choice:
 *   1. Select group type, then register a new savings group (admin path)
 *   2. Browse groups and join one (member path)
 *
 * The user's name is read from [UserProfileCacheService] for a personalised welcome.
 */
@Composable
fun NewUserOnboardingScreen(
    profileCache: UserProfileCacheService?,
    onRegisterGroup: (GroupType?) -> Unit,
    onBrowseGroups: () -> Unit,
    selectedGroupType: GroupType? = null
) {
    val clickDebouncer = rememberClickDebouncer()
    val fullName = profileCache?.getFullName()
    val firstName = fullName?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "there"
    // Keyed on the argument so a group type arriving from navigation replaces the restored
    // selection instead of being silently ignored by the saveable.
    val selectedGroupTypeState = rememberSaveable(selectedGroupType?.name) {
        mutableStateOf(selectedGroupType)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Welcome Badge ──────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👋", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ui_onboarding_account_ready),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Welcome, $firstName! 🎉",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.ui_onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            // ── Group Type Selection ────────────────────────────────────────────
            Text(
                text = stringResource(R.string.ui_onboarding_select_group_type),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            GroupTypeChip(
                groupTypes = GroupType.all(),
                selectedType = selectedGroupTypeState,
                onSelect = { groupType -> selectedGroupTypeState.value = groupType }
            )

            Spacer(Modifier.height(32.dp))

            // ── Option 1 — Register a Group ────────────────────────────────────
            OnboardingOptionCard(
                icon = Icons.Default.GroupAdd,
                badgeText = stringResource(R.string.ui_onboarding_for_group_leaders),
                title = stringResource(R.string.ui_onboarding_register_new_group),
                description = stringResource(R.string.ui_onboarding_register_group_desc),
                actionLabel = stringResource(R.string.ui_onboarding_create_group),
                onClick = { clickDebouncer.processClick { onRegisterGroup(selectedGroupTypeState.value) } }
            )

            Spacer(Modifier.height(20.dp))

            // ── Divider ─────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = stringResource(R.string.ui_or).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Option 2 — Browse & Join ────────────────────────────────────────
            OnboardingOptionCard(
                icon = Icons.Default.Search,
                badgeText = stringResource(R.string.ui_onboarding_for_members),
                title = stringResource(R.string.ui_onboarding_browse_join_group),
                description = stringResource(R.string.ui_onboarding_browse_desc),
                actionLabel = stringResource(R.string.ui_onboarding_browse_groups),
                isSecondary = true,
                onClick = { clickDebouncer.processClick(onBrowseGroups) }
            )

            Spacer(Modifier.height(36.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.ui_onboarding_switch_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingOptionCard(
    icon: ImageVector,
    badgeText: String,
    title: String,
    description: String,
    actionLabel: String,
    isSecondary: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isSecondary) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSecondary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(20.dp))

            if (isSecondary) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = actionLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                SanibonaniButton(
                    text = actionLabel,
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
        }
    }
}