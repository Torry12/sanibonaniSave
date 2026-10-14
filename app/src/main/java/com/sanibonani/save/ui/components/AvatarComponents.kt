package com.sanibonani.save.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.ui.theme.Forest

@Composable
fun InitialsAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = Forest,
    textColor: Color = Color.White,
    photoUrl: String? = null
) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photoUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Profile Photo",
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, background.copy(alpha = 0.5f), CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier          = modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(background, background.copy(alpha = 0.7f)))),
            contentAlignment  = Alignment.Center
        ) {
            Text(
                text       = initials.take(2).uppercase(),
                style      = MaterialTheme.typography.labelLarge,
                color      = textColor,
                fontSize   = (size.value * 0.32).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
