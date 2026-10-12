package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

@Composable
fun SaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, null) } },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun SanibonaniDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.CalendarToday, null)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onValueChange(date.toString())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
