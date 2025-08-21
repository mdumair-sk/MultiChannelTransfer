package com.neo.multichanneltransfer.models

data class ConnectionInfo(
    val type: ConnectionType,
    var isActive: Boolean = false,
    var isConnected: Boolean = false,
    var currentSpeedMbps: Double = 0.0,
    var progressPercent: Int = 0,
    var status: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

enum class ConnectionType {
    USB, WIFI
}

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, TRANSFERRING
}
