package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.theme.MidGray
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun NotificationsTab(state: AdminUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionHeading("🔔 System Notifications")
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.notifications) { notif ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Forest.copy(0.1f), modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Notifications, null, tint = Forest, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(notif.message, style = MaterialTheme.typography.bodyMedium)
                            Text(notif.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                    }
                }
            }
            if (state.notifications.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No new notifications", color = MidGray)
                    }
                }
            }
        }
    }
}
