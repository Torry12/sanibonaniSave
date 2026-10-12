package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.system.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class StorageRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : BaseRepository("StorageRepository"), StorageRepository {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    }

    private fun guessContentTypeFromPath(path: String): ContentType {
        val raw = when (path.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "csv" -> "text/csv"
            else -> "application/octet-stream"
        }
        return ContentType.parse(raw)
    }

    override suspend fun uploadFile(
        bucketName: String,
        path: String,
        byteArray: ByteArray,
        upsert: Boolean
    ): Result<String> = retryWithExponentialBackoff {
        require(byteArray.isNotEmpty()) { "Cannot upload an empty file." }
        require(byteArray.size <= MAX_FILE_SIZE_BYTES) { "File size (${byteArray.size / (1024 * 1024)}MB) exceeds 10MB limit." }

        val contentType = guessContentTypeFromPath(path)
        AppLogger.d(tag, "📤 Uploading file to $bucketName/$path (${byteArray.size} bytes, ${contentType.contentType}/${contentType.contentSubtype})")
        val bucket = supabase.storage[bucketName]
        bucket.upload(path, byteArray) {
            this.upsert = upsert
            // Helps Supabase & clients serve the file correctly.
            this.contentType = contentType
        }
        AppLogger.d(tag, "✅ Upload successful: $path")
        path
    }

    override suspend fun downloadFile(
        bucketName: String,
        path: String
    ): Result<ByteArray> = retryWithExponentialBackoff {
        AppLogger.d(tag, "📥 Downloading file from $bucketName/$path")
        val bucket = supabase.storage[bucketName]
        val bytes = bucket.downloadAuthenticated(path)
        AppLogger.d(tag, "✅ Download successful: ${bytes.size} bytes")
        bytes
    }

    override suspend fun deleteFile(
        bucketName: String,
        path: String
    ): Result<Unit> = retryWithExponentialBackoff {
        AppLogger.d(tag, "🗑️ Deleting file $bucketName/$path")
        val bucket = supabase.storage[bucketName]
        bucket.delete(path)
        AppLogger.d(tag, "✅ Delete successful: $path")
    }

    override suspend fun getPublicUrl(bucketName: String, path: String): String {
        // Private buckets requiring authenticated access.
        val privateBuckets = setOf("documents", "loan_contracts", "constitutions")

        return if (bucketName in privateBuckets) {
            val base = supabase.supabaseUrl.trimEnd('/')
            "$base/storage/v1/object/authenticated/$bucketName/$path"
        } else {
            supabase.storage[bucketName].publicUrl(path)
        }
    }

    override suspend fun createSignedUrl(
        bucketName: String,
        path: String,
        expiresIn: Long
    ): Result<String> = runCatching {
        AppLogger.d(tag, "🔐 Creating signed URL for $bucketName/$path (expires in ${expiresIn}s)")
        val bucket = supabase.storage[bucketName]
        val url = bucket.createSignedUrl(path, expiresIn.seconds)
        AppLogger.d(tag, "✅ Signed URL created")
        url
    }
}
