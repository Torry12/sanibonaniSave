package com.sanibonani.save.ui.screens.member.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.screens.admin.components.SectionHeaderCard
import com.sanibonani.save.ui.screens.member.getFileNameFromUri
import com.sanibonani.save.viewmodel.MemberProfileViewModel

@Composable
fun MemberDocumentsTab(
    member: Member?,
    group: Group?,
    vm: MemberProfileViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var activeIdx by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) vm.uploadDocument(activeIdx, bytes, getFileNameFromUri(context.contentResolver, it), "Document")
        }
    }

    var expandedPersonal by remember { mutableStateOf(true) }
    var expandedGroup by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionHeaderCard(
            title = "Personal Documents",
            subtitle = "Core KYC documents for your profile",
            icon = "👤",
            isExpanded = expandedPersonal,
            onClick = { expandedPersonal = !expandedPersonal }
        )

        if (expandedPersonal) {
            Spacer(Modifier.height(12.dp))
            val docs = listOf(
                "Identity Document (ID)" to member?.document1Url,
                "Proof of Residence" to member?.document2Url,
                "Beneficiary Documents" to member?.document3Url,
                "Marriage Certificate" to member?.document4Url,
                "Signed Constitution" to member?.document5Url
            )
            docs.forEachIndexed { i, doc ->
                val name = doc.first
                val url = doc.second
                DocumentUploadCard(
                    label = name,
                    isUploaded = !url.isNullOrBlank(),
                    status = when(i+1) {
                        1 -> member?.document1Status ?: DocumentStatus.PENDING
                        2 -> member?.document2Status ?: DocumentStatus.PENDING
                        3 -> member?.document3Status ?: DocumentStatus.PENDING
                        4 -> member?.document4Status ?: DocumentStatus.PENDING
                        5 -> member?.document5Status ?: DocumentStatus.PENDING
                        else -> DocumentStatus.PENDING
                    },
                    onUpload = { activeIdx = i + 1; launcher.launch("*/*") },
                    onView = if (!url.isNullOrBlank()) {
                        {
                            // val h = vm.getDownloadParams(url)
                            val h = emptyMap<String, String>()
                            val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                            val fileName = "${name.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                            onFileAction(url, fileName, h)
                        }
                    } else null
                )
            }
        }

        if (state.documents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeaderCard(
                title = "Group Specific Documents",
                subtitle = "Additional files required by ${group?.name ?: "your group"}",
                icon = "📂",
                isExpanded = expandedGroup,
                onClick = { expandedGroup = !expandedGroup }
            )

            if (expandedGroup) {
                Spacer(Modifier.height(12.dp))
                state.documents.forEach { doc ->
                    val relationalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        uri?.let {
                            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                            // if (bytes != null) vm.uploadRelationalDocument(doc.label, bytes, getFileNameFromUri(context.contentResolver, it))
                        }
                    }

                    DocumentUploadCard(
                        label = doc.label,
                        isUploaded = true,
                        status = doc.status,
                        onUpload = { relationalLauncher.launch("*/*") },
                        onView = {
                            // val h = vm.getDownloadParams(doc.documentUrl)
                            val h = emptyMap<String, String>()
                            val ext = doc.documentUrl.substringAfterLast(".", "pdf").substringBefore("?")
                            val fileName = "${doc.label.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                            onFileAction(doc.documentUrl, fileName, h)
                        }
                    )
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
                    // val h = vm.getDownloadParams(officialUrl)
                    val h = emptyMap<String, String>()
                    val fileName = "Group_Constitution_${group.name.replace(" ", "_")}.pdf"
                    onFileAction(officialUrl, fileName, h)
                } else {
                    // vm.downloadGroupConstitution()
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        InfoBox("Documents are encrypted and only accessible by admins.", InfoType.INFO)
        Spacer(Modifier.height(40.dp))
    }
}
