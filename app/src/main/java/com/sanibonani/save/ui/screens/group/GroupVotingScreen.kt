package com.sanibonani.save.ui.screens.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.PollStatus
import com.sanibonani.save.viewmodel.GroupVotingViewModel

@Composable
fun GroupVotingScreen(
    groupId: String,
    memberId: String?,
    vm: GroupVotingViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(groupId, memberId) {
        vm.loadPolls(groupId, memberId)
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Group Voting", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create Poll", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[index] = it },
                        label = { Text("Option ${index + 1}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { options.add("") }) { Text("Add Option") }
                    Button(onClick = {
                        vm.createPoll(
                            title = title,
                            description = description.ifBlank { null },
                            options = options.toList()
                        )
                        title = ""
                        description = ""
                        options.clear()
                        options.addAll(listOf("", ""))
                    }) { Text("Create") }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.polls, key = { it.poll.id.orEmpty() }) { item ->
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.poll.title, fontWeight = FontWeight.SemiBold)
                        item.poll.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text("Status: ${item.poll.status.name}")

                        item.options.forEach { option ->
                            val optionId = option.id.orEmpty()
                            val selected = item.myVoteOptionId == optionId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(option.label + if (selected) " (your vote)" else "")
                                if (item.poll.status == PollStatus.OPEN && !memberId.isNullOrBlank()) {
                                    Button(onClick = { vm.castVote(item.poll.id.orEmpty(), optionId) }) {
                                        Text(if (selected) "Change" else "Vote")
                                    }
                                }
                            }
                        }

                        if (item.poll.status == PollStatus.OPEN) {
                            Button(onClick = { vm.closePoll(item.poll.id.orEmpty()) }) {
                                Text("Close Poll")
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

