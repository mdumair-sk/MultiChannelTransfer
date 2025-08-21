package com.neo.multichanneltransfer.managers;

import com.neo.multichanneltransfer.models.FileChunk;
import com.neo.multichanneltransfer.models.ConnectionInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;

public class LoadBalancer {
    private HashMap<String, Queue<FileChunk>> channelQueues;
    private HashMap<String, Double> channelSpeeds;

    // Initial allocation percentages
    private static final double USB_INITIAL_ALLOCATION = 0.65; // 65%
    private static final double WIFI_INITIAL_ALLOCATION = 0.35; // 35%

    public LoadBalancer() {
        this.channelQueues = new HashMap<>();
        this.channelSpeeds = new HashMap<>();

        // Initialize queues
        channelQueues.put("usb", new LinkedList<>());
        channelQueues.put("wifi", new LinkedList<>());

        // Initialize expected speeds (MB/s)
        channelSpeeds.put("usb", 35.0);
        channelSpeeds.put("wifi", 20.0);
    }

    public void distributeChunks(ArrayList<FileChunk> chunks, HashMap<String, ConnectionInfo> connections) {
        // Get available connections
        ArrayList<String> availableChannels = new ArrayList<>();
        for (String channel : connections.keySet()) {
            ConnectionInfo conn = connections.get(channel);
            if (conn.isActive() && conn.isConnected()) {
                availableChannels.add(channel);
            }
        }

        if (availableChannels.isEmpty()) {
            return; // No connections available
        }

        // Clear existing queues
        for (Queue<FileChunk> queue : channelQueues.values()) {
            queue.clear();
        }

        // Initial distribution based on expected performance
        int totalChunks = chunks.size();
        int usbChunks = (int) (totalChunks * USB_INITIAL_ALLOCATION);
        int wifiChunks = totalChunks - usbChunks;

        // Distribute chunks
        for (int i = 0; i < chunks.size(); i++) {
            FileChunk chunk = chunks.get(i);

            if (i < usbChunks && availableChannels.contains("usb")) {
                chunk.setAssignedChannel("usb");
                channelQueues.get("usb").offer(chunk);
            } else if (availableChannels.contains("wifi")) {
                chunk.setAssignedChannel("wifi");
                channelQueues.get("wifi").offer(chunk);
            } else if (availableChannels.contains("usb")) {
                // Fallback to USB if WiFi not available
                chunk.setAssignedChannel("usb");
                channelQueues.get("usb").offer(chunk);
            }
        }
    }

    public void rebalanceChunks(HashMap<String, ConnectionInfo> connections) {
        // Update channel speeds based on current performance
        for (String channel : connections.keySet()) {
            ConnectionInfo conn = connections.get(channel);
            if (conn.isActive() && conn.isConnected()) {
                channelSpeeds.put(channel, conn.getCurrentSpeed());
            }
        }

        // Find the fastest and slowest channels
        String fastestChannel = null;
        String slowestChannel = null;
        double maxSpeed = 0;
        double minSpeed = Double.MAX_VALUE;

        for (String channel : channelSpeeds.keySet()) {
            if (connections.get(channel).isActive()) {
                double speed = channelSpeeds.get(channel);
                if (speed > maxSpeed) {
                    maxSpeed = speed;
                    fastestChannel = channel;
                }
                if (speed < minSpeed) {
                    minSpeed = speed;
                    slowestChannel = channel;
                }
            }
        }

        // Rebalance if there's significant speed difference (>15%)
        if (fastestChannel != null && slowestChannel != null &&
                !fastestChannel.equals(slowestChannel)) {

            double speedDifference = (maxSpeed - minSpeed) / minSpeed;
            if (speedDifference > 0.15) { // 15% threshold
                rebalanceQueues(fastestChannel, slowestChannel);
            }
        }
    }

    private void rebalanceQueues(String fastChannel, String slowChannel) {
        Queue<FileChunk> slowQueue = channelQueues.get(slowChannel);
        Queue<FileChunk> fastQueue = channelQueues.get(fastChannel);

        // Move some chunks from slow to fast channel
        int chunksToMove = Math.min(slowQueue.size() / 4, 10); // Move up to 25% or 10 chunks

        for (int i = 0; i < chunksToMove; i++) {
            FileChunk chunk = slowQueue.poll();
            if (chunk != null && "pending".equals(chunk.getStatus())) {
                chunk.setAssignedChannel(fastChannel);
                fastQueue.offer(chunk);
            }
        }
    }

    public FileChunk getNextChunk(String channel) {
        Queue<FileChunk> queue = channelQueues.get(channel);
        return queue != null ? queue.poll() : null;
    }

    public int getRemainingChunks(String channel) {
        Queue<FileChunk> queue = channelQueues.get(channel);
        return queue != null ? queue.size() : 0;
    }
}