package com.sanibonani.save.ui.screens.landing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.theme.Gold
import com.sanibonani.save.ui.theme.GoldLight

@Composable
fun LandingScreen(
    onNavigateLogin: (String?) -> Unit,
    onNavigateRegisterGroup: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateMemberPortal: () -> Unit,
    onNavigatePlatformAdmin: () -> Unit,
    viewModel: LandingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn = uiState.isLoggedIn
    val userRole = uiState.userRole

    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    Scaffold(
        containerColor = Forest
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
                    .height(480.dp)
            ) {
                // Background Placeholder or Image removed if R.drawable.sanibonani_hero missing

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Sanibonani Save",
                        color = Gold,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Empowering Communities Through Smart Savings",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isLoggedIn) {
                            if (userRole == UserRole.PLATFORM_ADMIN || uiState.isQualifyingPlatformAdmin) {
                                SanibonaniButton(
                                    "Platform Portal", onNavigatePlatformAdmin,
                                    modifier       = Modifier.weight(1f),
                                    containerColor = Gold,
                                    contentColor   = Forest
                                )
                            } else if (userRole == UserRole.GROUP_ADMIN) {
                                SanibonaniButton(
                                    "Admin Portal", onNavigateDashboard,
                                    modifier       = Modifier.weight(1f),
                                    containerColor = Gold,
                                    contentColor   = Forest
                                )
                            } else {
                                SanibonaniButton(
                                    "My Dashboard", onNavigateMemberPortal,
                                    modifier       = Modifier.weight(1f),
                                    containerColor = Gold,
                                    contentColor   = Forest
                                )
                            }
                        } else {
                            SanibonaniButton(
                                "Log In", { onNavigateLogin(null) },
                                modifier       = Modifier.weight(1f),
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor   = Color.White
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(10.dp))
                    
                    if (!isLoggedIn) {
                        TextButton(onClick = onNavigateRegisterGroup) {
                            Text("Don't have a group? Register now →", color = GoldLight,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        // Portal switching options for logged-in users
                        when {
                            userRole == UserRole.PLATFORM_ADMIN || uiState.isQualifyingPlatformAdmin -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Switch to another portal:", color = GoldLight.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = onNavigateDashboard) {
                                            Text("Group Admin", color = GoldLight, style = MaterialTheme.typography.labelLarge)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("•", color = GoldLight.copy(alpha = 0.4f))
                                        Spacer(Modifier.width(8.dp))
                                        TextButton(onClick = onNavigateMemberPortal) {
                                            Text("Member", color = GoldLight, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                            userRole == UserRole.GROUP_ADMIN -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onNavigateMemberPortal) {
                                        Text("Go to Member Portal →", color = GoldLight,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    TextButton(onClick = onNavigateRegisterGroup) {
                                        Text("Register new group →", color = GoldLight,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            else -> {
                                TextButton(onClick = onNavigateRegisterGroup) {
                                    Text("Register a new group →", color = GoldLight,
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // ── Stats strip ───────────────────────────────────────────────────────
            Surface(color = Gold, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (uiState.isLoading) {
                        repeat(4) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Forest,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    } else {
                        val stats = uiState.analytics
                        StatItem("Groups", stats.totalGroups.toString())
                        StatItem("Members", stats.totalMembers.toString())
                        StatItem("Savings", "R${stats.totalBalance}")
                        StatItem("Fees", "R${stats.totalPlatformFees}")
                    }
                }
            }

            // ── Information Sections ──────────────────────────────────────────────
            Column(Modifier.padding(24.dp)) {
                InfoSection(
                    "Transparent Governance",
                    "Real-time tracking of contributions, withdrawals, and loan disbursements with multi-level approval systems.",
                    Icons.Default.AccountBalance
                )
                InfoSection(
                    "Digital Ledger",
                    "Eliminate paper-based errors. Every transaction is recorded and accessible to all group members instantly.",
                    Icons.AutoMirrored.Filled.MenuBook
                )
                InfoSection(
                    "Smart Loans",
                    "Automated interest calculation and repayment tracking based on your group's unique constitution.",
                    Icons.AutoMirrored.Filled.TrendingUp
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Forest, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = Forest.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InfoSection(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
