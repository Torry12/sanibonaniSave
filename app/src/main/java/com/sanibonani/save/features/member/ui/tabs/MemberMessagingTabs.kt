package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.ui.components.SanibonaniSectionHeader
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.ui.MessageItem
import com.sanibonani.save.features.member.ui.NotificationItem
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.theme.Navy

@Composable
fun MemberNotificationsTab(notifications: List<AppNotification>) {
    var filterType by remember { mutableStateOf("ALL") }

    val filteredNotifications = remember(notifications, filterType) {
        when (filterType) {
            "SYSTEM" -> notifications.filter { it.channel == NotifChannel.PUSH }
            "FINANCIAL" -> notifications.filter { it.message.contains("payment", true) || it.message.contains("payout", true) || it.message.contains("loan", true) || it.message.contains("contribution", true) }
            else -> notifications
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🔔 Activity Notifications & Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Stay updated with your savings group activity, contribution receipts, and payout milestones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == "ALL",
                        onClick = { filterType = "ALL" },
                        label = { Text("All (${notifications.size})") }
                    )
                    FilterChip(
                        selected = filterType == "FINANCIAL",
                        onClick = { filterType = "FINANCIAL" },
                        label = { Text("Financial") }
                    )
                    FilterChip(
                        selected = filterType == "SYSTEM",
                        onClick = { filterType = "SYSTEM" },
                        label = { Text("System") }
                    )
                }
            }
        }

        if (filteredNotifications.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🔔", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            text = "You're all caught up!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No new notifications found in this category. Updates about your group contributions and payouts will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(items = filteredNotifications) { n: AppNotification ->
                NotificationItem(n)
            }
        }
    }
}

@Composable
fun MemberMessagesTab(
    messages: List<AppNotification>,
    vm: MemberOverviewViewModel? = null
) {
    val state by if (vm != null) vm.state.collectAsStateWithLifecycle() else remember { mutableStateOf(null) }
    val currentState = state

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (vm != null && currentState != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Send Message to Admin", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        SanibonaniTextField(
                            value = currentState.messageText,
                            onValueChange = { vm.updateMessageText(it) },
                            label = "Message content...",
                            modifier = Modifier.height(100.dp)
                        )

                        if (currentState.messageSentSuccess) {
                            Text("Message sent successfully!", color = Navy, style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { vm.sendMessageToAdmin() },
                            enabled = currentState.messageText.isNotBlank() && !currentState.isSendingMessage,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy)
                        ) {
                            if (currentState.isSendingMessage) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Send to Administrator")
                        }
                    }
                }
            }
        }

        item {
            SanibonaniSectionHeader("Messages from Admin")
        }

        if (messages.isEmpty()) {
            item {
                EmptyState(icon = "✉️", title = "No messages", description = "Admins haven't sent any messages yet.")
            }
        } else {
            items(items = messages) { m: AppNotification ->
                MessageItem(m)
            }
        }
    }
}
