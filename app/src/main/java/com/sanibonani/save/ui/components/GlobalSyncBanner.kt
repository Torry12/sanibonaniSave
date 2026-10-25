package com.sanibonani.save.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sanibonani.save.domain.repository.system.SyncStatus
import com.sanibonani.save.ui.theme.AccentBlue
import com.sanibonani.save.ui.theme.Navy

@Composable
fun GlobalSyncBanner(
    status: SyncStatus
) {
    AnimatedVisibility(
        visible = status is SyncStatus.Progress,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.zIndex(100f)
    ) {
        if (status is SyncStatus.Progress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = Navy.copy(alpha = 0.95f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🔄 ${status.message}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(status.progress * 100).toInt()}%",
                                color = AccentBlue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            color = AccentBlue,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}
