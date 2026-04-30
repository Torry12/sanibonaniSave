package com.sanibonani.save.ui.screens.landing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.formatZARShort
import com.sanibonani.save.ui.theme.*

@Composable
fun LandingScreen(
    onNavigateLogin: (String?) -> Unit,
    onNavigateRegisterGroup: () -> Unit,
    onNavigateBrowseGroups: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateMemberPortal: () -> Unit,
    viewModel: LandingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn = uiState.isLoggedIn
    val userRole = uiState.userRole
    val isQualifyingPlatformAdmin = uiState.isQualifyingPlatformAdmin

    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    Scaffold(
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Hero Section ──────────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Forest, ForestMid)
                                )
                            )
                            .padding(vertical = 48.dp, horizontal = 24.dp)
                    ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Gold,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            "TRUSTED BY COMMUNITIES",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Forest,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Sanibonani Save",
                        color = Gold,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Text(
                        "The Modern Digital Ledger for South African Savings Groups",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    
                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoggedIn) {
                            val primaryLabel = when {
                                userRole == UserRole.PLATFORM_ADMIN || isQualifyingPlatformAdmin -> "Platform Portal"
                                userRole == UserRole.GROUP_ADMIN -> "Admin Dashboard"
                                else -> "Member Portal"
                            }
                            val primaryAction = when {
                                userRole == UserRole.PLATFORM_ADMIN || isQualifyingPlatformAdmin -> onNavigateMemberPortal
                                userRole == UserRole.GROUP_ADMIN -> onNavigateDashboard
                                else -> onNavigateMemberPortal
                            }
                            SanibonaniButton(
                                text = primaryLabel,
                                onClick = primaryAction,
                                containerColor = Gold,
                                contentColor = Forest,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onNavigateBrowseGroups,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(
                                    "Browse Groups",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            SanibonaniButton(
                                text = "Browse Groups",
                                onClick = onNavigateBrowseGroups,
                                containerColor = Gold,
                                contentColor = Forest,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onNavigateRegisterGroup,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(
                                    "Register Group",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    // ...existing code...

                    if (isLoggedIn) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onNavigateRegisterGroup) {
                            Text("Register a new group →", color = GoldLight)
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { onNavigateLogin(null) }) {
                            Text("Already have an account? Log In →", color = GoldLight)
                        }
                    }
                }
            }

            // ── Stats Section ─────────────────────────────────────────────────
            Surface(
                color = Color.White,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().offset(y = (-20).dp).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp))
                    } else {
                        val stats = uiState.analytics
                        CompactStatItem("Groups", stats.totalGroups.toString())
                        VerticalDivider(modifier = Modifier.height(24.dp), color = LightGray)
                        CompactStatItem("Members", stats.totalMembers.toString())
                        VerticalDivider(modifier = Modifier.height(24.dp), color = LightGray)
                        CompactStatItem("Savings", formatZARShort(stats.totalBalance))
                    }
                }
            }

            // ── Core Features ─────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                SectionHeader("Designed for South Africa")
                Text(
                    "Tailored features for the unique needs of local savings communities.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MidGray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                FeatureCard(
                    title = "Burial Societies",
                    description = "Comprehensive actuarial tools, beneficiary management, and automated viability analysis for funeral insurance groups.",
                    icon = Icons.Default.Security,
                    iconColor = Forest
                )
                
                FeatureCard(
                    title = "Smart Loan System",
                    description = "Fair, group-backed lending with automated interest, contribution-based surety, and effortless repayment tracking.",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconColor = ForestMid
                )
                
                FeatureCard(
                    title = "Digital Stokvels",
                    description = "Eliminate paper ledgers. Real-time tracking of turns, payouts, and contributions for ROSCAs and Savings Clubs.",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    iconColor = Forest
                )

                FeatureCard(
                    title = "Group Discovery",
                    description = "Find and join trusted groups in your neighborhood using our interactive map and geolocated search.",
                    icon = Icons.Default.Map,
                    iconColor = ForestMid,
                    onClick = onNavigateBrowseGroups
                )
            }

            // ── Compliance & Security ──────────────────────────────────────────
            Surface(
                color = Forest.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, tint = Forest, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "Bank-Grade Security",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Forest
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your data and group finances are protected by multi-level RLS policies and secure document encryption. We ensure full transparency for every member while maintaining the highest privacy standards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Charcoal
                    )
                }
            }

            // ── Call to Action ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Forest)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Ready to digitize your group?",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Join thousands of members growing their wealth together.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                SanibonaniButton(
                    text = "Register Your Group Now",
                    onClick = onNavigateRegisterGroup,
                    containerColor = Gold,
                    contentColor = Forest,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun CompactStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Forest
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MidGray
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, LightGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Charcoal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MidGray,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = Charcoal,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
