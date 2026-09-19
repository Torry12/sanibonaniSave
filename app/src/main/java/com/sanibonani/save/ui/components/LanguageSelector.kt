package com.sanibonani.save.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.sanibonani.save.R

@Composable
fun LanguageSelector(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp)
) {
    val currentLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement
    ) {
        LanguageChip(
            label = stringResource(R.string.ui_english),
            selected = currentLocale == "en",
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) },
            modifier = Modifier.weight(1f)
        )
        LanguageChip(
            label = stringResource(R.string.ui_isizulu),
            selected = currentLocale == "zu",
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zu")) },
            modifier = Modifier.weight(1f)
        )
        LanguageChip(
            label = stringResource(R.string.ui_sesotho),
            selected = currentLocale == "st",
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("st")) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
