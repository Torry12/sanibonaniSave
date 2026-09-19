package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.domain.model.NotifChannel

@Composable
fun MessagingTab(state: AdminOverviewUiState, vm: AdminOverviewViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SanibonaniSectionHeader("Group Broadcast", subtitle = "Send a message to ALL members")

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = state.messageText,
                    onValueChange = { vm.updateMessageText(it) },
                    label = stringResource(R.string.ui_message_content),
                    singleLine = false,
                    modifier = Modifier.height(120.dp)
                )

                Text("Send Via:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotifChannel.entries.forEach { channel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.selectedNotifChannel == channel,
                                onClick = { vm.updateNotifChannel(channel) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = when(channel) {
                                    NotifChannel.IN_APP -> "In-App"
                                    NotifChannel.EMAIL -> "Email"
                                    NotifChannel.BOTH -> "Both"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                if (state.messageSentSuccess) {
                    InfoBox("Broadcast sent successfully!", InfoType.SUCCESS)
                }

                SanibonaniButton(
                    text = "Send Broadcast",
                    onClick = { vm.broadcastMessage() },
                    isLoading = state.isSendingMessage,
                    enabled = state.messageText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SanibonaniSectionHeader("History")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.memberMessages.isEmpty()) {
                item {
                    EmptyState(
                        icon = "✉️",
                        title = "No messages",
                        description = "Your broadcast history will appear here."
                    )
                }
            } else {
                items(state.memberMessages) { msg ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(msg.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(msg.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
