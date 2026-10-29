package com.sanibonani.save.features.landing.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.features.landing.presentation.LandingUiState
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ClickDebouncer

@Composable
internal fun HeroSection(
    uiState: LandingUiState,
    clickDebouncer: ClickDebouncer,
    onNavigateLogin: (String?) -> Unit,
    onNavigateRegisterGroup: () -> Unit,
    onNavigateBrowseGroups: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateMemberPortal: () -> Unit
) {
    val isLoggedIn = uiState.isLoggedIn
    val userRole = uiState.userRole

    // ── Hero Section ──────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition()
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HeroGradient"
    )

    var isHeroLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isHeroLoaded = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(NavyDeep, Navy, NavyMid, NavyLight),
                    start = Offset(gradientOffset, gradientOffset / 2),
                    end = Offset(gradientOffset + 800f, 1200f)
                )
            )
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = isHeroLoaded,
                enter = slideInVertically(initialOffsetY = { -40 }, animationSpec = tween(700, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(700))
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        stringResource(R.string.ui_trusted_by_communities),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = isHeroLoaded,
                enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delayMillis = 100, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(800, delayMillis = 100))
            ) {
                AutoFitText(
                    text = stringResource(R.string.ui_sanibonani_save),
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(
                visible = isHeroLoaded,
                enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delayMillis = 200, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(800, delayMillis = 200))
            ) {
                Text(
                    stringResource(R.string.ui_the_modern_digital_ledger_for_south_afri),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(
                visible = isHeroLoaded,
                enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delayMillis = 300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(800, delayMillis = 300))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // ⚡ Hero Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoggedIn) {
                            if (uiState.pendingGroup != null) {
                                SanibonaniButton(
                                    text = stringResource(R.string.ui_finish_registration),
                                    onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                                    containerColor = WarningAmber,
                                    contentColor = Navy,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.titleSmall
                                )
                            } else if (!uiState.isMemberOrAdmin) {
                                SanibonaniButton(
                                    text = stringResource(R.string.ui_join_a_group),
                                    onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) },
                                    containerColor = Color.White,
                                    contentColor = Navy,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.titleSmall
                                )
                            } else {
                                val primaryLabel = when (userRole) {
                                    UserRole.PLATFORM_ADMIN -> stringResource(R.string.ui_platform_portal)
                                    UserRole.GROUP_ADMIN    -> stringResource(R.string.ui_admin_dashboard_title)
                                    UserRole.MEMBER         -> stringResource(R.string.ui_member_portal_title)
                                }
                                val primaryAction = when (userRole) {
                                    UserRole.PLATFORM_ADMIN -> onNavigateDashboard
                                    UserRole.GROUP_ADMIN    -> onNavigateDashboard
                                    UserRole.MEMBER         -> onNavigateMemberPortal
                                }
                                SanibonaniButton(
                                    text = primaryLabel,
                                    onClick = { clickDebouncer.processClick(primaryAction) },
                                    containerColor = Color.White,
                                    contentColor = Navy,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.titleSmall
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(stringResource(R.string.ui_browse_all), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        } else {
                            SanibonaniButton(
                                text = stringResource(R.string.ui_browse_groups),
                                onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) },
                                containerColor = Color.White,
                                contentColor = Navy,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(stringResource(R.string.ui_register_group), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    if (isLoggedIn) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) }) {
                            Text(stringResource(R.string.ui_register_a_new_group), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { clickDebouncer.processClick { onNavigateLogin(null) } }) {
                            Text(stringResource(R.string.ui_already_a_member_log_in), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
