package com.sanibonani.save.features.member.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.ui.getFileNameFromUri
import com.sanibonani.save.features.member.presentation.MemberProfileViewModel
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel

@Composable
fun MemberDocumentsTab(
    member: Member?,
    group: Group?,
    vm: MemberProfileViewModel,
    overviewVm: MemberOverviewViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var activeIdx by remember { mutableIntStateOf(0) }
    var activeRelationalLabel by remember { mutableStateOf<String?>(null) }

    val personalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            bytes?.let { vm.uploadDocument(activeIdx, it, getFileNameFromUri(context.contentResolver, uri), "Document") }
        }
    }

    val relationalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            activeRelationalLabel?.let { label ->
                val bytes = context.contentResolver.openInputStream(selectedUri)?.readBytes()
                if (bytes != null) vm.uploadRelationalDocument(label, bytes, getFileNameFromUri(context.contentResolver, selectedUri), "application/octet-stream")
            }
        }
    }

    var expandedPersonal by remember { mutableStateOf(true) }
    var expandedGroup by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionHeaderCard(
            title = "Personal Documents",
            subtitle = "Core KYC documents for your profile",
            emoji = "ðŸ‘¤",
            isExpanded = expandedPersonal,
            onClick = { expandedPersonal = !expandedPersonal }
        )

        androidx.compose.animation.AnimatedVisibility(visible = expandedPersonal) {
            Column {
                Spacer(Modifier.height(12.dp))
                val docs = listOf(
                    "Identity Document (ID)" to member?.document1Url,
                    "Proof of Residence" to member?.document2Url,
                    "Marriage Certificate" to member?.document4Url
                )
                docs.forEachIndexed { i, doc ->
                    val name = doc.first
                    val url = doc.second
                    DocumentUploadCard(
                        label = name,
                        isUploaded = !url.isNullOrBlank(),
                        imageUrl = url,
                        status = when(i) {
                            0 -> member?.document1Status ?: DocumentStatus.PENDING
                            1 -> member?.document2Status ?: DocumentStatus.PENDING
                            2 -> member?.document4Status ?: DocumentStatus.PENDING
                            else -> DocumentStatus.PENDING
                        },
                        onUpload = {
                            activeIdx = when(i) {
                                0 -> 1
                                1 -> 2
                                2 -> 4
                                else -> 1
                            }
                            personalLauncher.launch("*/*")
                        },
                        onView = if (!url.isNullOrBlank()) {
                            {
                                val h = vm.getDownloadParams(url)
                                val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                                val fileName = "${name.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                onFileAction(url, fileName, h)
                            }
                        } else null
                    )
                }
            }
        }

        if (state.documents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeaderCard(
                title = "Group Specific Documents",
                subtitle = "Additional files required by ${group?.name ?: "your group"}",
                emoji = "ðŸ“‚",
                isExpanded = expandedGroup,
                onClick = { expandedGroup = !expandedGroup }
            )

            androidx.compose.animation.AnimatedVisibility(visible = expandedGroup) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    state.documents.forEach { doc ->
                        DocumentUploadCard(
                            label = doc.label,
                            isUploaded = true,
                            imageUrl = doc.documentUrl,
                            status = doc.status,
                            onUpload = {
                                activeRelationalLabel = doc.label
                                relationalLauncher.launch("*/*")
                            },
                            onView = {
                                val h = vm.getDownloadParams(doc.documentUrl)
                                val ext = doc.documentUrl.substringAfterLast(".", "pdf").substringBefore("?")
                                val fileName = "${doc.label.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                onFileAction(doc.documentUrl, fileName, h)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        ModernNavigationLink(
            title = "Group Constitution",
            subtitle = "Read the latest group rules and policy",
            icon = Icons.Default.Description,
            onClick = {
                val officialUrl = group?.constitutionUrl
                if (!officialUrl.isNullOrBlank()) {
                    val h = vm.getDownloadParams(officialUrl)
                    val fileName = "Group_Constitution_${group.name.replace(" ", "_")}.pdf"
                    onFileAction(officialUrl, fileName, h)
                } else {
                    overviewVm.downloadGroupConstitution()
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        ModernNavigationLink(
            title = "Membership Certificate",
            subtitle = "Download your official membership document",
            icon = Icons.Default.VerifiedUser,
            onClick = { overviewVm.downloadMembershipContract() }
        )

        Spacer(Modifier.height(24.dp))
        InfoBox("Documents are encrypted and only accessible by admins.", type = InfoType.INFO)
        Spacer(Modifier.height(40.dp))
    }
}
