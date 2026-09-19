package com.sanibonani.save.features.group.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.features.group.ui.GroupDiscoveryCard
import com.sanibonani.save.ui.theme.Cream
import com.sanibonani.save.features.group.presentation.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(vm: GroupViewModel, onGroupClick: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { vm.loadGroups() },
        modifier = Modifier.fillMaxSize().background(Cream)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.rawGroups.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        icon = "🔍",
                        title = "No groups found",
                        description = "Join a group to start saving together."
                    )
                }
            }

            items(state.filteredGroups) { group ->
                GroupListItem(group = group, onClick = { onGroupClick(group.id ?: "") })
            }
        }
    }
}

@Composable
fun GroupListItem(group: Group, onClick: () -> Unit) {
    GroupDiscoveryCard(group = group, onClick = onClick)
}
