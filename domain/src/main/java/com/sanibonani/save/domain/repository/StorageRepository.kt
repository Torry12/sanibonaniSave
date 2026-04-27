package com.sanibonani.save.domain.repository

interface StorageRepository {
    /**
     * Upload a file to storage bucket.
     * @param bucketName The storage bucket name (documents, constitutions, avatars)
     * @param path The file path within the bucket
     * @param byteArray The file content as bytes
     * @param upsert Whether to overwrite existing file
     * @return Result containing the uploaded file path on success
     */
    suspend fun uploadFile(
        bucketName: String,
        path: String,
        byteArray: ByteArray,
        upsert: Boolean = true
    ): Result<String>

    /**
     * Download a file from storage bucket.
     * @param bucketName The storage bucket name
     * @param path The file path within the bucket
     * @return Result containing the file content as bytes
     */
    suspend fun downloadFile(
        bucketName: String,
        path: String
    ): Result<ByteArray>

    /**
     * Delete a file from storage bucket.
     * @param bucketName The storage bucket name
     * @param path The file path within the bucket
     * @return Result indicating success or failure
     */
    suspend fun deleteFile(
        bucketName: String,
        path: String
    ): Result<Unit>

    /**
     * Get the public URL for a file.
     * For public buckets, this returns a direct URL.
     * For private buckets, use createSignedUrl instead.
     */
    suspend fun getPublicUrl(bucketName: String, path: String): String

    /**
     * Create a signed URL for temporary access to a private file.
     * @param bucketName The storage bucket name
     * @param path The file path within the bucket
     * @param expiresIn Expiration time in seconds (default 1 hour)
     * @return Result containing the signed URL
     */
    suspend fun createSignedUrl(
        bucketName: String,
        path: String,
        expiresIn: Long = 3600
    ): Result<String>
}
