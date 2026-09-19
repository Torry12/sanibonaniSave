package com.sanibonani.save.features.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.features.admin.ui.components.AdminFeeBanner
import com.sanibonani.save.ui.components.SecondaryScrollableTabRow
import com.sanibonani.save.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardTopBar(
    title: String,
    groupName: String?,
    isSupportMode: Boolean,
    onNavigateBack: () -> Unit,
    memberPortalAccess: AdminMemberPortalAccess,
    onNavigateToMemberPortal: (String, String?) -> Unit,
    feeStatus: AdminFeeState,
    group: Group?,
    daysOverdue: Int,
    onPayRegistration: (Money, String) -> Unit,
    managedGroups: List<Group>,
    onGroupSelected: (String) -> Unit,
    currentTab: Int,
    tabs: List<Pair<Int, String>>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: CoroutineScope,
    onTabSelected: (Int) -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    groupName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            navigationIcon = {
                if (isSupportMode) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val gid = memberPortalAccess.groupId
                        if (!gid.isNullOrBlank() && memberPortalAccess.enabled) {
                            onNavigateToMemberPortal(gid, memberPortalAccess.memberId)
                        }
                    },
                    enabled = memberPortalAccess.enabled
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = memberPortalAccess.buttonLabel,
                        tint = if (memberPortalAccess.enabled) Navy else MidGray
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (feeStatus == AdminFeeState.PENDING_ACTIVATION && group != null) {
            AdminFeeBanner(
                group = group,
                status = feeStatus,
                daysOverdue = daysOverdue,
                onAction = {
                    group.id?.let { id ->
                        onPayRegistration(group.registrationFee, id)
                    }
                }
            )
        }

        if (managedGroups.size > 1) {
            var showMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Navy.copy(0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SwapHoriz, null, tint = Navy, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_switch_group) + ": ${groupName ?: "..."}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowDropDown, null, tint = Navy)
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
                ) {
                    managedGroups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.name, fontWeight = if (g.id == group?.id) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                g.id?.let { onGroupSelected(it) }
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (g.id == group?.id) Icons.Default.CheckCircle else Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = if (g.id == group?.id) Navy else LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            ) {
                tabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = pagerState.currentPage == i,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(i)
                                onTabSelected(tab.first)
                            }
                        },
                        text = { Text(tab.second, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminDashboardBottomBar(
    memberPortalAccess: AdminMemberPortalAccess,
    onNavigateToMemberPortal: (String, String?) -> Unit
) {
    Surface(shadowElevation = 8.dp, tonalElevation = 2.dp, color = MaterialTheme.colorScheme.onPrimary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val gid = memberPortalAccess.groupId
                    if (!gid.isNullOrBlank() && memberPortalAccess.enabled) {
                        onNavigateToMemberPortal(gid, memberPortalAccess.memberId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = memberPortalAccess.enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = memberPortalAccess.buttonLabel,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
