package com.neo.multichanneltransfer.managers

import com.neo.multichanneltransfer.models.ConnectionInfo
import com.neo.multichanneltransfer.models.ConnectionType
import com.neo.multichanneltransfer.models.FileChunk
import java.util.*
import kotlin.collections.ArrayList

class LoadBalancer {

    private val channelQueues: MutableMap<ConnectionType, Queue<FileChunk>> = EnumMap(ConnectionType::class.java)
    private val channelSpeeds: MutableMap<ConnectionType, Double> = EnumMap(ConnectionType::class.java)

    companion object {
        private const val USB_INITIAL_ALLOCATION = 0.65
        private const val WIFI_INITIAL_ALLOCATION = 0.35
        private const val SPEED_DIFF_THRESHOLD = 0.15 // 15%
    }

    init {
        channelQueues[ConnectionType.USB] = LinkedList()
        channelQueues[ConnectionType.WIFI] = LinkedList()

        channelSpeeds[ConnectionType.USB] = 35.0
        channelSpeeds[ConnectionType.WIFI] = 20.0
    }

    fun distributeChunks(chunks: List<FileChunk>, connections: Map<ConnectionType, ConnectionInfo>) {
        val availableChannels = connections.filterValues { it.isActive && it.isConnected }.keys.toList()

        if (availableChannels.isEmpty()) return

        // Clear old queues
        channelQueues.values.forEach { it.clear() }

        val totalChunks = chunks.size
        val usbChunksCount = (totalChunks * USB_INITIAL_ALLOCATION).toInt()
        val wifiChunksCount = totalChunks - usbChunksCount

        chunks.forEachIndexed { i, chunk ->
            when {
                i < usbChunksCount && availableChannels.contains(ConnectionType.USB) -> {
                    chunk.assignedChannel = ConnectionType.USB
                    channelQueues[ConnectionType.USB]?.offer(chunk)
                }
                availableChannels.contains(ConnectionType.WIFI) -> {
                    chunk.assignedChannel = ConnectionType.WIFI
                    channelQueues[ConnectionType.WIFI]?.offer(chunk)
                }
                availableChannels.contains(ConnectionType.USB) -> {
                    chunk.assignedChannel = ConnectionType.USB
                    channelQueues[ConnectionType.USB]?.offer(chunk)
                }
            }
        }
    }

    fun rebalanceChunks(connections: Map<ConnectionType, ConnectionInfo>) {
        connections.forEach { (channel, conn) ->
            if (conn.isActive && conn.isConnected) {
                channelSpeeds[channel] = conn.currentSpeedMbps
            }
        }

        val activeSpeeds = channelSpeeds.filterKeys { key ->
            connections[key]?.isActive == true
        }

        if (activeSpeeds.size < 2) return

        val (fastestChannel, maxSpeed) = activeSpeeds.maxByOrNull { it.value } ?: return
        val (slowestChannel, minSpeed) = activeSpeeds.minByOrNull { it.value } ?: return

        if (fastestChannel == slowestChannel) return

        val speedDifference = (maxSpeed - minSpeed) / minSpeed
        if (speedDifference <= SPEED_DIFF_THRESHOLD) return

        rebalanceQueues(fastestChannel, slowestChannel)
    }

    private fun rebalanceQueues(fastChannel: ConnectionType, slowChannel: ConnectionType) {
        val slowQueue = channelQueues[slowChannel] ?: return
        val fastQueue = channelQueues[fastChannel] ?: return

        val chunksToMove = minOf(slowQueue.size / 4, 10)

        repeat(chunksToMove) {
            val chunk = slowQueue.poll()
            if (chunk != null && chunk.status == com.neo.multichanneltransfer.models.ChunkStatus.PENDING) {
                chunk.assignedChannel = fastChannel
                fastQueue.offer(chunk)
            }
        }
    }

    fun getNextChunk(channel: ConnectionType): FileChunk? {
        return channelQueues[channel]?.poll()
    }

    fun getRemainingChunks(channel: ConnectionType): Int {
        return channelQueues[channel]?.size ?: 0
    }
}
