package com.neo.multichanneltransfer.models

data class FileChunk(
    val chunkId: String,
    val chunkIndex: Int,
    val sizeBytes: Int,
    var data: ByteArray? = null,
    var checksum: String? = null,
    var status: ChunkStatus = ChunkStatus.PENDING,
    var progressPercent: Int = 0,
    var assignedChannel: ConnectionType? = null
)

enum class ChunkStatus {
    PENDING, TRANSFERRING, COMPLETED, FAILED
}
