package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.domain.model.NotifChannel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessagingTab(state: AdminOverviewUiState, vm: AdminOverviewViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SanibonaniSectionHeader("Group Broadcast", subtitle = "Send a message to ALL members")
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SanibonaniTextField(
                        value = state.messageText,
                        onValueChange = { vm.updateMessageText(it) },
                        label = stringResource(R.string.ui_message_content),
                        singleLine = false,
                        modifier = Modifier.height(140.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Send Via:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NotifChannel.entries.forEach { channel ->
                                val isSelected = state.selectedNotifChannel == channel
                                val (label, icon) = when(channel) {
                                    NotifChannel.PUSH -> "Push" to Icons.Default.NotificationsActive
                                    NotifChannel.EMAIL -> "Email" to Icons.Default.Email
                                    NotifChannel.SMS -> "SMS" to Icons.Default.Sms
                                    NotifChannel.WHATSAPP -> "WhatsApp" to Icons.AutoMirrored.Filled.Chat
                                    NotifChannel.BOTH -> "Both" to Icons.Default.AllInclusive
                                }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { vm.updateNotifChannel(channel) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    if (state.messageSentSuccess) {
                        InfoBox("Broadcast sent successfully!", type = InfoType.SUCCESS)
                    }

                    Spacer(Modifier.height(8.dp))

                    SanibonaniButton(
                        text = "Send Broadcast",
                        onClick = { vm.broadcastMessage() },
                        isLoading = state.isSendingMessage,
                        enabled = state.messageText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            SanibonaniSectionHeader("History")
        }

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
