package com.neo.multichanneltransfer.models;

public class ConnectionInfo {
    private String type; // "usb" or "wifi"
    private boolean isActive;
    private boolean isConnected;
    private double currentSpeed; // MB/s
    private int progress; // 0-100
    private String status; // "disconnected", "connecting", "connected", "transferring"

    public ConnectionInfo(String type) {
        this.type = type;
        this.isActive = false;
        this.isConnected = false;
        this.currentSpeed = 0.0;
        this.progress = 0;
        this.status = "disconnected";
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isConnected() { return isConnected; }
    public void setConnected(boolean connected) { isConnected = connected; }

    public double getCurrentSpeed() { return currentSpeed; }
    public void setCurrentSpeed(double currentSpeed) { this.currentSpeed = currentSpeed; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}