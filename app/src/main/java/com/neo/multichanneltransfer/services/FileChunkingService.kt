package com.neo.multichanneltransfer.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neo.multichanneltransfer.models.FileChunk
import com.neo.multichanneltransfer.models.ChunkStatus
import com.neo.multichanneltransfer.models.ConnectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

class FileChunkingService(private val context: Context) {

    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES = 512 * 1024 // 512 KB
        private const val TAG = "FileChunkingService"
    }

    suspend fun chunkFile(
        fileUri: Uri,
        fileName: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE_BYTES,
        progressCallback: ((Int) -> Unit)? = null
    ): List<FileChunk> = withContext(Dispatchers.IO) {
        val chunks = mutableListOf<FileChunk>()

        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
            val totalSize = inputStream.available().toLong()
            var bytesReadTotal = 0L
            var chunkIndex = 0

            val buffer = ByteArray(chunkSize)
            var readBytes: Int

            while (inputStream.read(buffer).also { readBytes = it } != -1) {
                val chunkData = buffer.copyOf(readBytes)
                val chunkId = "${fileName}_chunk_$chunkIndex"
                val checksum = calculateChecksum(chunkData)

                val chunk = FileChunk(
                    chunkId = chunkId,
                    chunkIndex = chunkIndex,
                    sizeBytes = readBytes,
                    data = chunkData,
                    checksum = checksum,
                    status = ChunkStatus.PENDING,
                    assignedChannel = null
                )

                chunks.add(chunk)
                chunkIndex++

                bytesReadTotal += readBytes
                val progressPercent = ((bytesReadTotal * 100) / totalSize).toInt()
                progressCallback?.invoke(progressPercent)
            }
        } ?: throw IllegalArgumentException("Unable to open file input stream for URI: $fileUri")

        chunks
    }

    private fun calculateChecksum(data: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Checksum calculation failed", e)
            ""
        }
    }
}
