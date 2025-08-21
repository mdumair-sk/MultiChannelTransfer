package com.neo.multichanneltransfer.services

import android.util.Log
import com.neo.multichanneltransfer.models.*
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class TransferService(
    coroutineContext: CoroutineContext = Dispatchers.IO
) {

    interface TransferCallback {
        fun onChunkTransferComplete(chunk: FileChunk)
        fun onChunkTransferProgress(chunk: FileChunk, progress: Int)
        fun onChunkTransferError(chunk: FileChunk, error: String)
    }

    private val TAG = "TransferService"
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    private var usbSocket: Socket? = null
    private var wifiSocket: Socket? = null

    private var usbOutputStream: DataOutputStream? = null
    private var wifiOutputStream: DataOutputStream? = null

    private val usbChunkQueue = ConcurrentLinkedQueue<FileChunk>()
    private val wifiChunkQueue = ConcurrentLinkedQueue<FileChunk>()

    private var usbSenderJob: Job? = null
    private var wifiSenderJob: Job? = null

    suspend fun openConnection(channel: ConnectionType, pcIp: String, port: Int) {
        withContext(Dispatchers.IO) {
            when (channel) {
                ConnectionType.USB -> {
                    if (usbSocket?.isConnected != true) {
                        usbSocket = Socket().apply { connect(InetSocketAddress(pcIp, port), 5000) }
                        usbOutputStream = DataOutputStream(usbSocket!!.getOutputStream())
                    }
                }
                ConnectionType.WIFI -> {
                    if (wifiSocket?.isConnected != true) {
                        wifiSocket = Socket().apply { connect(InetSocketAddress(pcIp, port), 5000) }
                        wifiOutputStream = DataOutputStream(wifiSocket!!.getOutputStream())
                    }
                }
            }
        }
    }

    suspend fun sendTransferMetadata(
        outputStream: DataOutputStream,
        transferId: String,
        fileName: String,
        fileSize: Long,
        totalChunks: Int
    ) {
        withContext(Dispatchers.IO) {
            val transferIdBytes = transferId.toByteArray(StandardCharsets.UTF_8)
            outputStream.writeInt(transferIdBytes.size)
            outputStream.write(transferIdBytes)

            val fileNameBytes = fileName.toByteArray(StandardCharsets.UTF_8)
            outputStream.writeInt(fileNameBytes.size)
            outputStream.write(fileNameBytes)

            outputStream.writeLong(fileSize)
            outputStream.writeInt(totalChunks)
            outputStream.flush()
        }
    }

    private fun startSender(
        channel: ConnectionType,
        outputStream: DataOutputStream,
        callback: TransferCallback
    ): Job {
        return scope.launch {
            val queue = if (channel == ConnectionType.USB) usbChunkQueue else wifiChunkQueue
            while (isActive) {
                val chunk = queue.poll()
                if (chunk != null) {
                    try {
                        sendChunk(outputStream, chunk, callback)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending chunk: ${e.message}", e)
                        chunk.status = ChunkStatus.FAILED
                        withContext(Dispatchers.Main) {
                            callback.onChunkTransferError(chunk, e.localizedMessage ?: "Send error")
                        }
                        // Optionally handle retries here
                    }
                } else {
                    delay(50)
                }
            }
        }
    }

    private suspend fun sendChunk(
        outputStream: DataOutputStream,
        chunk: FileChunk,
        callback: TransferCallback
    ) = withContext(Dispatchers.IO) {
        val transferIdBytes = chunk.chunkId.toByteArray(StandardCharsets.UTF_8)
        outputStream.writeInt(transferIdBytes.size)
        outputStream.write(transferIdBytes)

        val checksumBytes = (chunk.checksum ?: "").toByteArray(StandardCharsets.UTF_8)
        outputStream.writeInt(checksumBytes.size)
        outputStream.write(checksumBytes)

        outputStream.writeInt(chunk.chunkIndex)
        outputStream.writeInt(chunk.sizeBytes)

        val buffer = chunk.data ?: ByteArray(0)
        val totalBytes = buffer.size
        var bytesSent = 0
        val blockSize = 8192 // 8KB

        while (bytesSent < totalBytes) {
            val toWrite = minOf(blockSize, totalBytes - bytesSent)
            outputStream.write(buffer, bytesSent, toWrite)
            bytesSent += toWrite

            val progress = (bytesSent * 100) / totalBytes
            withContext(Dispatchers.Main) {
                callback.onChunkTransferProgress(chunk, progress)
            }
        }
        outputStream.flush()
        chunk.status = ChunkStatus.COMPLETED
        withContext(Dispatchers.Main) {
            callback.onChunkTransferComplete(chunk)
        }
    }

    fun enqueueChunk(chunk: FileChunk) {
        when (chunk.assignedChannel) {
            ConnectionType.USB -> usbChunkQueue.add(chunk)
            ConnectionType.WIFI -> wifiChunkQueue.add(chunk)
            else -> {}
        }
    }

    suspend fun closeConnections() {
        withContext(Dispatchers.IO) {
            usbSenderJob?.cancelAndJoin()
            wifiSenderJob?.cancelAndJoin()
            usbOutputStream?.close()
            wifiOutputStream?.close()
            usbSocket?.close()
            wifiSocket?.close()
        }
    }

    fun startTransferSession(
        pcIp: String,
        port: Int,
        transferId: String,
        fileName: String,
        fileSize: Long,
        totalChunks: Int,
        callback: TransferCallback
    ) {
        scope.launch {
            try {
                openConnection(ConnectionType.USB, pcIp, port)
                openConnection(ConnectionType.WIFI, pcIp, port)

                usbOutputStream?.let {
                    sendTransferMetadata(it, transferId, fileName, fileSize, totalChunks)
                    usbSenderJob = startSender(ConnectionType.USB, it, callback)
                }

                wifiOutputStream?.let {
                    sendTransferMetadata(it, transferId, fileName, fileSize, totalChunks)
                    wifiSenderJob = startSender(ConnectionType.WIFI, it, callback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transfer session: ${e.message}", e)
            }
        }
    }
}
