package com.neo.multichanneltransfer.services

import android.content.Context
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.neo.multichanneltransfer.models.ConnectionInfo
import com.neo.multichanneltransfer.models.ConnectionStatus
import com.neo.multichanneltransfer.models.ConnectionType

class ConnectionService(private val context: Context) {
    private val connections = mutableMapOf<ConnectionType, ConnectionInfo>()

    init {
        connections[ConnectionType.WIFI] = ConnectionInfo(ConnectionType.WIFI)
        connections[ConnectionType.USB] = ConnectionInfo(ConnectionType.USB)
    }

    fun detectConnections() {
        detectWifiConnection()
        detectUsbConnection()
    }

    fun getConnection(type: ConnectionType): ConnectionInfo? = connections[type]

    fun getAllConnections(): Map<ConnectionType, ConnectionInfo> = connections.toMap()

    private fun detectWifiConnection() {
        val wifiConn = connections[ConnectionType.WIFI] ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = network?.let { cm.getNetworkCapabilities(it) }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            ni?.type == ConnectivityManager.TYPE_WIFI && ni.isConnected
        }

        wifiConn.isActive = isConnected
        wifiConn.isConnected = isConnected
        wifiConn.status = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
    }

    private fun detectUsbConnection() {
        val usbConn = connections[ConnectionType.USB] ?: return
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        val isConnected = deviceList.isNotEmpty()

        usbConn.isActive = isConnected
        usbConn.isConnected = isConnected
        usbConn.status = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
    }
}
