package com.sanibonani.save.features.landing

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanibonani.save.ui.components.AsyncResultHandler
import com.sanibonani.save.ui.utils.ClickDebouncer
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.features.landing.components.CompactStatItem
import com.sanibonani.save.features.landing.components.FeatureCard
import com.sanibonani.save.features.landing.components.FeatureDetail
import com.sanibonani.save.features.landing.components.FeatureDetailDialog
import com.sanibonani.save.features.landing.components.GroupTypeHelpItem
import com.sanibonani.save.features.landing.components.HowItWorksItem
import com.sanibonani.save.features.landing.components.StatsShimmer

@Composable
fun LandingScreen(
    onNavigateLogin: (String?) -> Unit,
    onNavigateRegisterGroup: () -> Unit,
    onNavigateBrowseGroups: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateMemberPortal: () -> Unit,
    viewModel: LandingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    var selectedFeature by remember { mutableStateOf<FeatureDetail?>(null) }

    // Auto-refresh when coming back online
    LaunchedEffect(uiState.isSystemOnline) {
        if (uiState.isSystemOnline && uiState.analytics.getOrNull() == null) {
            viewModel.onIntent(LandingIntent.Refresh)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LandingHeader(
                isOnline = uiState.isSystemOnline,
                isAirplaneMode = uiState.isAirplaneMode
            )

            HeroSection(
                uiState = uiState,
                clickDebouncer = clickDebouncer,
                onNavigateLogin = onNavigateLogin,
                onNavigateRegisterGroup = onNavigateRegisterGroup,
                onNavigateBrowseGroups = onNavigateBrowseGroups,
                onNavigateDashboard = onNavigateDashboard,
                onNavigateMemberPortal = onNavigateMemberPortal
            )

            StatsSection(
                analytics = uiState.analytics,
                onRetry = { viewModel.onIntent(LandingIntent.Refresh) }
            )

            FeaturesSection(
                clickDebouncer = clickDebouncer,
                onSelectFeature = { selectedFeature = it },
                onNavigateBrowseGroups = onNavigateBrowseGroups
            )

            ComplianceSection()

            CallToActionSection(
                clickDebouncer = clickDebouncer,
                onNavigateRegisterGroup = onNavigateRegisterGroup
            )

            HowItWorksSection()

            AboutAndHelpSection()

            Spacer(Modifier.height(48.dp))
        }
    }

    selectedFeature?.let { detail ->
        FeatureDetailDialog(
            detail = detail,
            onDismiss = { selectedFeature = null }
        )
    }
}

@Composable
private fun LandingHeader(isOnline: Boolean, isAirplaneMode: Boolean) {
    // ── Network Status ────────────────────────────────────────────────
    if (!isOnline || isAirplaneMode) {
        OfflineBanner(
            isOnline = false,
            message = if (isAirplaneMode) stringResource(R.string.ui_offline_airplane_mode)
                      else stringResource(R.string.ui_offline_cached_mode)
        )
    }

    // ── Language Selector ─────────────────────────────────────────────
    LanguageSelector(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun HeroSection(
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

@Composable
private fun StatsSection(
    analytics: AsyncResult<PlatformAnalytics>,
    onRetry: () -> Unit
) {
    // ── Stats Section ─────────────────────────────────────────────────
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-24).dp)
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            AsyncResultHandler(
                result = analytics,
                onLoading = { StatsShimmer() },
                onError = { message, _ ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.ui_retry_connection_btn), fontWeight = FontWeight.Bold)
                    }
                }
            ) { stats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val groupsText = if (stats.totalGroups > 0) stats.totalGroups.toString() else stringResource(R.string.ui_new)
                    val membersText = if (stats.totalMembers > 0) stats.totalMembers.toString() else stringResource(R.string.ui_join_now)

                    CompactStatItem(stringResource(R.string.ui_groups_active), groupsText)
                    Box(Modifier.width(1.dp).height(32.dp).background(LightGray.copy(alpha = 0.5f)))
                    CompactStatItem(stringResource(R.string.ui_trusted_members), membersText)
                }
            }
        }
    }
}

@Composable
private fun FeaturesSection(
    clickDebouncer: ClickDebouncer,
    onSelectFeature: (FeatureDetail) -> Unit,
    onNavigateBrowseGroups: () -> Unit
) {
    // ── Core Features ─────────────────────────────────────────────────
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        SanibonaniSectionHeader(stringResource(R.string.ui_designed_for_south_africa))
        Text(stringResource(R.string.ui_tailored_features_for_the_unique_needs_o),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        FeatureCard(
            title = stringResource(R.string.ui_burial_societies_title),
            description = stringResource(R.string.ui_burial_societies_desc),
            icon = Icons.Default.Security,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = {
                onSelectFeature(
                    FeatureDetail(
                        title = R.string.ui_burial_societies_title,
                        description = R.string.ui_burial_societies_desc,
                        icon = Icons.Default.Security,
                        iconColor = Navy,
                        benefits = listOf(
                            R.string.ui_benefit_burial_1,
                            R.string.ui_benefit_burial_2,
                            R.string.ui_benefit_burial_3,
                            R.string.ui_benefit_burial_4
                        )
                    )
                )
            }
        )

        FeatureCard(
            title = stringResource(R.string.ui_smart_loan_system_title),
            description = stringResource(R.string.ui_smart_loan_system_desc),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = {
                onSelectFeature(
                    FeatureDetail(
                        title = R.string.ui_smart_loan_system_title,
                        description = R.string.ui_smart_loan_system_desc,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconColor = NavyMid,
                        benefits = listOf(
                            R.string.ui_benefit_loans_1,
                            R.string.ui_benefit_loans_2,
                            R.string.ui_benefit_loans_3,
                            R.string.ui_benefit_loans_4
                        )
                    )
                )
            }
        )

        FeatureCard(
            title = stringResource(R.string.ui_digital_stokvels_title),
            description = stringResource(R.string.ui_digital_stokvels_desc),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = {
                onSelectFeature(
                    FeatureDetail(
                        title = R.string.ui_digital_stokvels_title,
                        description = R.string.ui_digital_stokvels_desc,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconColor = Navy,
                        benefits = listOf(
                            R.string.ui_benefit_stokvel_1,
                            R.string.ui_benefit_stokvel_2,
                            R.string.ui_benefit_stokvel_3,
                            R.string.ui_benefit_stokvel_4
                        )
                    )
                )
            }
        )

        FeatureCard(
            title = stringResource(R.string.ui_group_discovery_title),
            description = stringResource(R.string.ui_group_discovery_desc),
            icon = Icons.Default.Map,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) }
        )
    }
}

@Composable
private fun ComplianceSection() {
    // ── Compliance & Security ──────────────────────────────────────────
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.ui_bankgrade_security),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.ui_your_data_and_group_finances_are_protect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CallToActionSection(
    clickDebouncer: ClickDebouncer,
    onNavigateRegisterGroup: () -> Unit
) {
    // ── Call to Action Section ──────────────────────────────────────
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui_start_your_legacy),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_start_legacy_desc, PlatformFees.REGISTRATION.format()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            SanibonaniButton(
                text = stringResource(R.string.ui_register_group_now),
                onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}

@Composable
private fun HowItWorksSection() {
    // ── How It Works ──────────────────────────────────────────────────
    Column(Modifier.padding(24.dp)) {
        SanibonaniSectionHeader(stringResource(R.string.ui_how_it_works))
        Spacer(Modifier.height(16.dp))

        HowItWorksItem(
            step = "1",
            title = stringResource(R.string.ui_step_create_group),
            description = stringResource(R.string.ui_step_create_group_desc)
        )
        HowItWorksItem(
            step = "2",
            title = stringResource(R.string.ui_step_invite_members),
            description = stringResource(R.string.ui_step_invite_members_desc)
        )
        HowItWorksItem(
            step = "3",
            title = stringResource(R.string.ui_step_save_grow),
            description = stringResource(R.string.ui_step_save_grow_desc)
        )
    }
}

@Composable
private fun AboutAndHelpSection() {
    // ── About, Owner Creditation, Contact & Group Types Help ───────────
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ℹ️ About SanibonaniSave", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "SanibonaniSave is South Africa's premier digital savings platform, designed to digitize informal community finance with transparent ledgers, automated banking reconciliation, and actuarial risk modeling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                Text("👑 App Owner & Creditation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Founded and Architected by Torry Msimango (Lead Platform Architect & Developer). Built with passion to empower African community wealth-building.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📝 Company Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                // NOTE: no personal/admin email here. This card is on the public
                // landing screen, and exposing an administrator's personal address
                // (especially a platform-admin account) is a phishing and targeting
                // vector. Route support through the public channel only.
                Text("Email: support@sanibonani.save", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Phone: +27 (0) 11 555 0192", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Address: Sandton City Office Towers, 5th Street, Sandton, Johannesburg, 2196, South Africa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("❓ Help & Group Types Guide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Understanding the different savings structures supported on SanibonaniSave:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GroupTypeHelpItem("Burial Society", "Mutual aid societies providing death benefit payouts, actuarial solvency monitoring, and contribution tracking.")
                GroupTypeHelpItem("Stokvel", "Traditional rotating or cumulative savings schemes where members contribute fixed monthly amounts.")
                GroupTypeHelpItem("ROSCA", "Rotating Savings and Credit Association with automated turn-taking, random draws, and bidding auctions.")
                GroupTypeHelpItem("Investment Club", "Equity and stock-pooling groups utilizing unit-based Net Asset Value (NAV) and annualised CAGR returns.")
                GroupTypeHelpItem("Emergency Fund", "Immediate liquidity reserves with strict withdrawal caps and instant disbursal validation.")
                GroupTypeHelpItem("Community Savings", "Community co-op pools supporting micro-loans and collective grassroots welfare.")
                GroupTypeHelpItem("Tontine", "Longevity-linked mutual fund where surviving members share accumulated yields.")
                GroupTypeHelpItem("Other", "General purpose custom savings and financial groups.")
            }
        }
    }
}
