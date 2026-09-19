package com.sanibonani.save.ui.screens.member.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.components.SectionHeader
import com.sanibonani.save.ui.screens.member.MessageItem
import com.sanibonani.save.ui.screens.member.NotificationItem

@Composable
fun MemberNotificationsTab(notifications: List<AppNotification>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (notifications.isEmpty()) item { EmptyState(icon = "🔔", title = "No notifications", description = "You're all caught up!") }
        else items(items = notifications) { n: AppNotification -> NotificationItem(n) }
    }
}

@Composable
fun MemberMessagesTab(messages: List<AppNotification>) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionHeader("Messages from Admin")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item { EmptyState(icon = "✉️", title = "No messages", description = "Admins haven't sent any messages yet.") }
            else items(items = messages) { m: AppNotification -> MessageItem(m) }
        }
    }
}
