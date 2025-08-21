package com.neo.multichanneltransfer.models

import java.util.*

data class TransferSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSizeBytes: Long,
    val chunks: MutableList<FileChunk> = mutableListOf(),
    var totalChunks: Int = 0,
    var completedChunks: Int = 0,
    var startTime: Date = Date(),
    var endTime: Date? = null,
    var status: SessionStatus = SessionStatus.PREPARING,
    var overallProgressPercent: Double = 0.0
)

enum class SessionStatus {
    PREPARING, TRANSFERRING, COMPLETED, FAILED
}
