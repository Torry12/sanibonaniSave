package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.repository.GroupDocumentRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Provider

class GroupDocumentRepositoryImpl @Inject constructor(
    private val supabaseProvider: Provider<SupabaseClient>,
    private val groupRepo: Provider<GroupRepository>,
    private val dbProvider: Provider<SanibonaniDatabase>,
    private val storageRepo: StorageRepository,
) : BaseRepository("GroupDocumentRepository"), GroupDocumentRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override suspend fun uploadConstitution(
        groupId: String,
        fileBytes: ByteArray,
        fileName: String,
    ): Result<String> = retryWithExponentialBackoff {
        runCatching {
            fun sanitizeFileName(input: String): String {
                val base = input
                    .substringAfterLast('\\')
                    .substringAfterLast('/')
                    .trim()

                val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
                return cleaned.ifBlank { "constitution_${System.currentTimeMillis()}.pdf" }
            }

            val safeName = sanitizeFileName(fileName)
            val timestamp = System.currentTimeMillis()
            val fileNameWithTimestamp = if (safeName.contains(".")) {
                val name = safeName.substringBeforeLast(".")
                val ext = safeName.substringAfterLast(".")
                "${name}_$timestamp.$ext"
            } else {
                "${safeName}_$timestamp"
            }

            val path = "$groupId/$fileNameWithTimestamp"

            AppLogger.d(tag, "📤 Uploading constitution to constitutions/$path (${fileBytes.size} bytes)")
            val uploadedPath = storageRepo.uploadFile("constitutions", path, fileBytes).getOrThrow()
            val url = storageRepo.getPublicUrl("constitutions", uploadedPath)

            // Update the group record with the new URL and reset status to PENDING
            supabase.postgrest["groups"].update(buildJsonObject {
                put("constitution_url", url)
                put("constitution_status", DocumentStatus.PENDING.name.lowercase())
            }) {
                filter { eq("id", groupId) }
            }

            // Update local Room cache
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(
                    constitutionUrl = url,
                    constitutionStatus = DocumentStatus.PENDING
                ))
            }

            // Refresh the group in cache
            groupRepo.get().getGroupById(groupId)

            url
        }
    }
}
