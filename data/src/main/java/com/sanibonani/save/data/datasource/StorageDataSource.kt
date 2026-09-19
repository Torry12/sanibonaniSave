package com.sanibonani.save.data.datasource

interface StorageDataSource {
    suspend fun uploadFile(bucketName: String, path: String, byteArray: ByteArray, upsert: Boolean = true): Result<String>
    suspend fun downloadFile(bucketName: String, path: String): Result<ByteArray>
    suspend fun deleteFile(bucketName: String, path: String): Result<Unit>
    suspend fun getPublicUrl(bucketName: String, path: String): String
    suspend fun createSignedUrl(bucketName: String, path: String, expiresIn: Long = 3600): Result<String>
}
