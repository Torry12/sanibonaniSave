package com.sanibonani.save.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.ui.theme.MidGray

@Composable
fun AutoCompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<Feature>,
    onSuggestionClick: (Feature) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var textFieldSize by remember { mutableStateOf(Size.Zero) }

    Column(modifier = modifier) {
        SanibonaniTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    textFieldSize = coordinates.size.toSize()
                },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        )

        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .width(with(LocalDensity.current) { textFieldSize.width.toDp() }),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(suggestion) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = suggestion.properties.formatted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = listOfNotNull(
                                        suggestion.properties.suburb,
                                        suggestion.properties.city,
                                        suggestion.properties.state
                                    ).joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MidGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

