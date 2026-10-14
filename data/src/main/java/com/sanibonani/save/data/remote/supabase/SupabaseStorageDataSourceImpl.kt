package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.StorageDataSource
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseStorageDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : StorageDataSource {

    override suspend fun uploadFile(bucketName: String, path: String, byteArray: ByteArray, upsert: Boolean): Result<String> = safeRunCatching {
        val bucket = client.storage.from(bucketName)
        bucket.upload(path, byteArray) { this.upsert = upsert }
        path
    }

    override suspend fun downloadFile(bucketName: String, path: String): Result<ByteArray> = safeRunCatching {
        client.storage.from(bucketName).downloadPublic(path)
    }

    override suspend fun deleteFile(bucketName: String, path: String): Result<Unit> = safeRunCatching {
        client.storage.from(bucketName).delete(path)
    }

    override suspend fun getPublicUrl(bucketName: String, path: String): String {
        return client.storage.from(bucketName).publicUrl(path)
    }

    override suspend fun createSignedUrl(bucketName: String, path: String, expiresIn: Long): Result<String> = safeRunCatching {
        client.storage.from(bucketName).createSignedUrl(path, expiresIn.seconds)
    }
}
