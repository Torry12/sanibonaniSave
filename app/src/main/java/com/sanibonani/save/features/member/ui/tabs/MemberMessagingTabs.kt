package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.components.SanibonaniSectionHeader
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.member.ui.MessageItem
import com.sanibonani.save.features.member.ui.NotificationItem
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel

@Composable
fun MemberNotificationsTab(notifications: List<AppNotification>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (notifications.isEmpty()) item { EmptyState(icon = "🔔", title = "No notifications", description = "You're all caught up!") }
        else items(items = notifications) { n: AppNotification -> NotificationItem(n) }
    }
}

@Composable
fun MemberMessagesTab(
    messages: List<AppNotification>,
    vm: MemberOverviewViewModel? = null
) {
    val state by if (vm != null) vm.state.collectAsStateWithLifecycle() else remember { mutableStateOf(null) }
    val currentState = state

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (vm != null && currentState != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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

        SanibonaniSectionHeader("Messages from Admin")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item { EmptyState(icon = "✉️", title = "No messages", description = "Admins haven't sent any messages yet.") }
            else items(items = messages) { m: AppNotification -> MessageItem(m) }
        }
    }
}
