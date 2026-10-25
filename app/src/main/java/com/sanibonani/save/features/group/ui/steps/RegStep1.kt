package com.sanibonani.save.features.group.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.RoscaRotationMethod
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.components.SelectionCard
import com.sanibonani.save.features.group.ui.components.SectionHeader
import com.sanibonani.save.features.group.ui.components.groupTypeDescription
import com.sanibonani.save.ui.components.GroupIcon
import com.sanibonani.save.ui.utils.PhoneNumberTransformation

/** Step 1: group name, contact, type, rotation method and icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegStep1(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader(
            title = stringResource(R.string.ui_basic_info),
            description = stringResource(R.string.ui_tell_us_the_name_and_type_of_your_group)
        )

        SanibonaniTextField(
            value = s.name,
            onValueChange = { vm.onEvent(GroupFormEvent.NameChanged(it)) },
            label = "Group Name *",
            isError = s.fieldErrors.containsKey("name"),
            supportingText = s.fieldErrors["name"]
        )

        SanibonaniTextField(
            value = s.adminEmail,
            onValueChange = { vm.onEvent(GroupFormEvent.AdminEmailChanged(it)) },
            label = "Group Email *",
            isError = s.fieldErrors.containsKey("adminEmail"),
            supportingText = s.fieldErrors["adminEmail"]
        )

        SanibonaniTextField(
            value = s.adminPhone,
            onValueChange = { if (it.length <= 10) vm.onEvent(GroupFormEvent.AdminPhoneChanged(it)) },
            label = stringResource(R.string.ui_primary_contact_number_required),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneNumberTransformation(),
            isError = s.fieldErrors.containsKey("adminPhone"),
            supportingText = s.fieldErrors["adminPhone"]
        )

        SectionHeader(
            title = stringResource(R.string.ui_group_type),
            description = "Select the category that best matches your goals"
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupType.entries.forEach { type ->
                SelectionCard(
                    selected = s.type == type,
                    onClick = { vm.onEvent(GroupFormEvent.TypeSelected(type)) },
                    title = type.displayName,
                    description = groupTypeDescription(type)
                )
            }
        }

        if (s.type == GroupType.ROSCA) {
            SectionHeader(
                title = stringResource(R.string.ui_rosca_rotation_method),
                description = "Choose how payouts will be ordered"
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RoscaRotationMethod.entries.forEach { method ->
                    SelectionCard(
                        selected = s.rotationMethod == method,
                        onClick = { vm.onEvent(GroupFormEvent.RoscaRotationMethodSelected(method)) },
                        title = method.displayName,
                        description = method.description
                    )
                }
            }
        }

        SectionHeader(
            title = stringResource(R.string.ui_group_icon),
            description = stringResource(R.string.ui_choose_an_emoji_to_represent_your_group)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupIcon(
                    logoEmoji = s.logoEmoji,
                    size = 72.dp,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )

                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("\uD83E\uDD1D", "\uD83D\uDCB0", "\uD83C\uDFE0", "\uD83D\uDCC8", "\uD83D\uDD4A️", "\uD83D\uDEE1️", "\uD83C\uDDFF\uD83C\uDDE6", "\uD83D\uDD25", "\uD83D\uDC8E", "\uD83C\uDF1F").forEach { emoji ->
                        val isSelected = s.logoEmoji == emoji
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { vm.onEvent(GroupFormEvent.LogoEmojiSelected(emoji)) }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
