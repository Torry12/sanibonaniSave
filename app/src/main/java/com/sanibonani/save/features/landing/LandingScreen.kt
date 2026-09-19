package com.sanibonani.save.features.landing

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformFees

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
    val isLoggedIn = uiState.isLoggedIn
    val userRole = uiState.userRole
    val clickDebouncer = rememberClickDebouncer()
    val context = LocalContext.current

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
            // ── Network Status ──
            if (!uiState.isSystemOnline || uiState.isAirplaneMode) {
                OfflineBanner(
                    message = if (uiState.isAirplaneMode) "⚠️ Airplane Mode Active — Data syncing is paused."
                              else "⚠️ Offline Mode — Using cached information."
                )
            }

            // ── Hero Section ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Navy, NavyMid)))
                    .padding(vertical = 48.dp, horizontal = 24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(bottom = 16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            stringResource(R.string.ui_trusted_by_communities),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        stringResource(R.string.ui_sanibonani_save),
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(R.string.ui_the_modern_digital_ledger_for_south_afri),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )

                    Spacer(Modifier.height(32.dp))

                    // ⚡ Hero Actions (Context Aware)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoggedIn) {
                            if (uiState.pendingGroup != null) {
                                // Admin with unpaid group
                                SanibonaniButton(
                                    text = "Finish Registration",
                                    onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                                    containerColor = WarningAmber,
                                    contentColor = Navy,
                                    modifier = Modifier.weight(1f)
                                )
                            } else if (!uiState.isMemberOrAdmin) {
                                // Logged in but no group yet
                                SanibonaniButton(
                                    text = "Join a Group",
                                    onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) },
                                    containerColor = Color.White,
                                    contentColor = Navy,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                // Active user
                                val primaryLabel = when (userRole) {
                                    UserRole.PLATFORM_ADMIN -> "Platform Portal"
                                    UserRole.GROUP_ADMIN    -> "Admin Dashboard"
                                    UserRole.MEMBER         -> "Member Portal"
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
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Browse All", fontWeight = FontWeight.ExtraBold)
                            }
                        } else {
                            // Guest
                            SanibonaniButton(
                                text = "Browse Groups",
                                onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) },
                                containerColor = Color.White,
                                contentColor = Navy,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(stringResource(R.string.ui_register_group), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    if (isLoggedIn) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) }) {
                            Text(stringResource(R.string.ui_register_a_new_group), color = Color.White)
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { clickDebouncer.processClick { onNavigateLogin(null) } }) {
                            Text(stringResource(R.string.ui_already_a_member_log_in), color = Color.White)
                        }
                    }
                }
            }

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
                    if (uiState.analytics.isLoading) {
                        StatsShimmer()
                    } else {
                        uiState.analytics.getOrNull()?.let { stats ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val groupsText = if (stats.totalGroups > 0) stats.totalGroups.toString() else "New"
                                val membersText = if (stats.totalMembers > 0) stats.totalMembers.toString() else "Join Now"

                                CompactStatItem("Groups Active", groupsText)
                                Box(Modifier.width(1.dp).height(32.dp).background(LightGray.copy(alpha = 0.5f)))
                                CompactStatItem("Trusted Members", membersText)
                            }
                        }
                    }

                    val analytics = uiState.analytics
                    if (analytics is AsyncResult.Error) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = analytics.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { viewModel.onIntent(LandingIntent.Refresh) }) {
                            Text("Retry Connection", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Core Features ─────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                SanibonaniSectionHeader("Designed for South Africa")
                Text(stringResource(R.string.ui_tailored_features_for_the_unique_needs_o),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                FeatureCard(
                    title = "Burial Societies",
                    description = "Comprehensive actuarial tools, beneficiary management, and automated viability analysis for funeral insurance groups.",
                    icon = Icons.Default.Security,
                    iconColor = Navy,
                    onClick = {
                        selectedFeature = FeatureDetail(
                            title = "Burial Societies",
                            description = "Professional tools designed for the unique needs of South African funeral insurance groups.",
                            icon = Icons.Default.Security,
                            iconColor = Navy,
                            benefits = listOf(
                                "Actuarial Oversight: Automated solvency and reserve adequacy calculations.",
                                "Beneficiary Management: Securely track and verify member beneficiaries.",
                                "Claim Processing: Streamlined workflows for death benefit claims and payouts.",
                                "Viability Analysis: Real-time insights into the group's long-term financial health."
                            )
                        )
                    }
                )

                FeatureCard(
                    title = "Smart Loan System",
                    description = "Fair, group-backed lending with automated interest, contribution-based surety, and effortless repayment tracking.",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconColor = NavyMid,
                    onClick = {
                        selectedFeature = FeatureDetail(
                            title = "Smart Loan System",
                            description = "A fair, transparent credit system backed by the collective savings of the group.",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            iconColor = NavyMid,
                            benefits = listOf(
                                "Automated Interest: Configure flat or reducing interest rates that accrue automatically.",
                                "Surety Protection: Use a member's savings principal as collateral for secure lending.",
                                "Repayment Tracking: Real-time visibility into installments and overdue statuses.",
                                "P2P Growth: Interest earned on loans stays within the group, growing the collective pot."
                            )
                        )
                    }
                )

                FeatureCard(
                    title = "Digital Stokvels",
                    description = "Eliminate paper ledgers. Real-time tracking of turns, payouts, and contributions for ROSCAs and Savings Clubs.",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    iconColor = Navy,
                    onClick = {
                        selectedFeature = FeatureDetail(
                            title = "Digital Stokvels",
                            description = "Modernize your stokvel or ROSCA by replacing physical ledgers with a secure digital record.",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            iconColor = Navy,
                            benefits = listOf(
                                "Turn Management: Automated rotation tracking for ROSCAs and payout clubs.",
                                "Instant Reconciliation: Real-time matching of member payments to bank records.",
                                "Transparency: Every member can see their contribution history and upcoming turns.",
                                "Immutable Ledger: Every transaction is cryptographically ledgered for maximum trust."
                            )
                        )
                    }
                )

                FeatureCard(
                    title = "Group Discovery",
                    description = "Find and join trusted groups in your neighborhood using our interactive map and geolocated search.",
                    icon = Icons.Default.Map,
                    iconColor = NavyMid,
                    onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) }
                )
            }

            // ── Compliance & Security ──────────────────────────────────────────
            // ...existing code...
            Surface(
                color = Navy.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, tint = Navy, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.ui_bankgrade_security),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ui_your_data_and_group_finances_are_protect),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Call to Action Section ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .background(
                        Brush.linearGradient(listOf(NavyLight.copy(0.1f), Cream)),
                        RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, Navy.copy(0.05f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Start Your Legacy",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Navy
                    )
                    Text(
                        "Secure your community's future in less than 5 minutes. Register your group today for only ${PlatformFees.REGISTRATION.format()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.DarkGray
                    )
                    SanibonaniButton(
                        text = "Register Group Now",
                        onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                        containerColor = Navy,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // ── How It Works ──────────────────────────────────────────────────
            Column(Modifier.padding(24.dp)) {
                SanibonaniSectionHeader("How it Works")
                Spacer(Modifier.height(16.dp))

                HowItWorksItem(
                    step = "1",
                    title = "Create Your Group",
                    description = "Register your burial society, stokvel, or club. Define your own rules, fees, and payouts."
                )
                HowItWorksItem(
                    step = "2",
                    title = "Invite Members",
                    description = "Add members manually or let them find you on the map. Every member gets their own secure portal."
                )
                HowItWorksItem(
                    step = "3",
                    title = "Save & Grow",
                    description = "Track contributions in real-time. Apply for loans, manage claims, and watch your collective wealth grow."
                )
            }

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
fun HowItWorksItem(step: String, title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = Navy,
            contentColor = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(step, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


data class FeatureDetail(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val benefits: List<String>
)

@Composable
fun FeatureDetailDialog(
    detail: FeatureDetail,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        },
        icon = {
            Surface(
                color = detail.iconColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(detail.icon, null, tint = detail.iconColor)
                }
            }
        },
        title = {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.benefits.forEach { benefit ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("•", color = detail.iconColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun CompactStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MidGray.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StatsShimmer() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp, 24.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(40.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(brush))
        }
        Box(Modifier.width(1.dp).height(32.dp).background(Color.LightGray.copy(alpha = 0.2f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp, 24.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(40.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(brush))
        }
    }
}
