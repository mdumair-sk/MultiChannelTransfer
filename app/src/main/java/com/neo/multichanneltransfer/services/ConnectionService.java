package com.neo.multichanneltransfer.services;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.hardware.usb.UsbManager;
import com.neo.multichanneltransfer.models.ConnectionInfo;
import java.util.HashMap;

public class ConnectionService {
    private Context context;
    private HashMap<String, ConnectionInfo> connections;

    public ConnectionService(Context context) {
        this.context = context;
        this.connections = new HashMap<>();
        initializeConnections();
    }

    private void initializeConnections() {
        connections.put("wifi", new ConnectionInfo("wifi"));
        connections.put("usb", new ConnectionInfo("usb"));
    }

    public void detectConnections() {
        // Check WiFi connection
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        ConnectionInfo wifiConn = connections.get("wifi");
        if (wifiInfo != null && wifiInfo.isConnected()) {
            wifiConn.setActive(true);
            wifiConn.setConnected(true);
            wifiConn.setStatus("connected");
        } else {
            wifiConn.setActive(false);
            wifiConn.setConnected(false);
            wifiConn.setStatus("disconnected");
        }

        // Check USB connection (simplified)
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        ConnectionInfo usbConn = connections.get("usb");

        // This is a simplified check - you'd need more complex logic for actual USB detection
        boolean usbConnected = usbManager.getDeviceList().size() > 0;
        usbConn.setActive(usbConnected);
        usbConn.setConnected(usbConnected);
        usbConn.setStatus(usbConnected ? "connected" : "disconnected");
    }

    public HashMap<String, ConnectionInfo> getConnections() {
        return connections;
    }

    public ConnectionInfo getConnection(String type) {
        return connections.get(type);
    }

    public boolean isConnectionAvailable(String type) {
        ConnectionInfo conn = connections.get(type);
        return conn != null && conn.isActive() && conn.isConnected();
    }
}