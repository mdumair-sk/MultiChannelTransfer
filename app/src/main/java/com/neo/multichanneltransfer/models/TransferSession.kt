package com.neo.multichanneltransfer.models;
import java.util.ArrayList;
import java.util.Date;

public class TransferSession {
    private String sessionId;
    private String fileName;
    private long fileSize;
    private ArrayList<FileChunk> chunks;
    private int totalChunks;
    private int completedChunks;
    private Date startTime;
    private Date endTime;
    private String status; // "preparing", "transferring", "completed", "failed"
    private double overallProgress; // 0-100

    public TransferSession(String fileName, long fileSize) {
        this.sessionId = java.util.UUID.randomUUID().toString();
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunks = new ArrayList<>();
        this.status = "preparing";
        this.overallProgress = 0.0;
        this.startTime = new Date();
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public ArrayList<FileChunk> getChunks() { return chunks; }
    public void setChunks(ArrayList<FileChunk> chunks) { this.chunks = chunks; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public int getCompletedChunks() { return completedChunks; }
    public void setCompletedChunks(int completedChunks) { this.completedChunks = completedChunks; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getOverallProgress() { return overallProgress; }
    public void setOverallProgress(double overallProgress) { this.overallProgress = overallProgress; }
}