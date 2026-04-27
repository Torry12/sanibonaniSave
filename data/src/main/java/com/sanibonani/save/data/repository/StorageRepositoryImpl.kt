package com.sanibonani.save.data.repository

import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.repository.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class StorageRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : BaseRepository("StorageRepository"), StorageRepository {

    private fun guessContentTypeFromPath(path: String): ContentType {
        val raw = when (path.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        return ContentType.parse(raw)
    }

    override suspend fun uploadFile(
        bucketName: String,
        path: String,
        byteArray: ByteArray,
        upsert: Boolean
    ): Result<String> = runCatching {
        retryWithExponentialBackoff {
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
    }

    override suspend fun downloadFile(
        bucketName: String,
        path: String
    ): Result<ByteArray> = runCatching {
        retryWithExponentialBackoff {
            AppLogger.d(tag, "📥 Downloading file from $bucketName/$path")
            val bucket = supabase.storage[bucketName]
            val bytes = bucket.downloadAuthenticated(path)
            AppLogger.d(tag, "✅ Download successful: ${bytes.size} bytes")
            bytes
        }
    }

    override suspend fun deleteFile(
        bucketName: String,
        path: String
    ): Result<Unit> = runCatching {
        retryWithExponentialBackoff {
            AppLogger.d(tag, "🗑️ Deleting file $bucketName/$path")
            val bucket = supabase.storage[bucketName]
            bucket.delete(path)
            AppLogger.d(tag, "✅ Delete successful: $path")
        }
    }

    override suspend fun getPublicUrl(bucketName: String, path: String): String {
        // Both `documents` and `avatars` are private buckets in this project.
        // A `publicUrl()` points to `/object/public/...` which fails for private buckets
        // even when an Authorization header is attached, because Supabase's storage gateway
        // rejects authenticated-only objects on the public endpoint regardless of headers.
        // Use the authenticated endpoint for all app-managed buckets so Coil + auth headers work.
        return if (bucketName == "documents" || bucketName == "avatars") {
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
