package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.theme.MidGray
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun MessagingTab(state: AdminUiState, vm: AdminViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("📢 Broadcast Message")
        Text("Send a message to all members via App Notifications and WhatsApp.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = state.messageText,
                    onValueChange = { vm.updateMessageText(it) },
                    label = "Your message...",
                    modifier = Modifier.height(150.dp)
                )

                if (state.messageSentSuccess) {
                    InfoBox("Message broadcasted successfully!", com.sanibonani.save.ui.components.InfoType.SUCCESS)
                }

                SanibonaniButton(
                    text = "Broadcast to All Members",
                    onClick = { vm.broadcastMessage() },
                    isLoading = state.isSendingMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text("Recent Broadcasts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f)) {
            items(state.memberMessages) { msg ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.5f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                        Text(msg.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                }
            }
        }
    }
}
