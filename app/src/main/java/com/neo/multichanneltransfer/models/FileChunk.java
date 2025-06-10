package com.neo.multichanneltransfer.models;

public class FileChunk {
    private String id;
    private int sequenceNumber;
    private long size;
    private String checksum;
    private byte[] data;
    private String status; // "pending", "transferring", "completed", "failed"
    private String assignedChannel; // "usb", "wifi"
    private int progress; // 0-100

    // Constructor
    public FileChunk(String id, int sequenceNumber, long size) {
        this.id = id;
        this.sequenceNumber = sequenceNumber;
        this.size = size;
        this.status = "pending";
        this.progress = 0;
    }

    // Getters and Setters (like C# properties)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedChannel() { return assignedChannel; }
    public void setAssignedChannel(String assignedChannel) { this.assignedChannel = assignedChannel; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}