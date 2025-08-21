package com.neo.multichanneltransfer.services;

import com.neo.multichanneltransfer.models.FileChunk;
import com.neo.multichanneltransfer.models.ConnectionInfo;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferService {
    private ExecutorService executorService;
    private TransferCallback callback;

    public interface TransferCallback {
        void onChunkTransferComplete(FileChunk chunk);
        void onChunkTransferProgress(FileChunk chunk, int progress);
        void onChunkTransferError(FileChunk chunk, String error);
    }

    public TransferService() {
        this.executorService = Executors.newFixedThreadPool(2); // One for each connection type
    }

    public void transferChunk(FileChunk chunk, ConnectionInfo connection, TransferCallback callback) {
        this.callback = callback;

        executorService.submit(() -> {
            try {
                if ("wifi".equals(connection.getType())) {
                    transferViaWifi(chunk);
                } else if ("usb".equals(connection.getType())) {
                    transferViaUsb(chunk);
                }
            } catch (Exception e) {
                callback.onChunkTransferError(chunk, e.getMessage());
            }
        });
    }

    private void transferViaWifi(FileChunk chunk) throws Exception {
        // Simulate WiFi transfer with progress updates
        chunk.setStatus("transferring");

        for (int progress = 0; progress <= 100; progress += 10) {
            Thread.sleep(100); // Simulate transfer time
            chunk.setProgress(progress);

            if (callback != null) {
                callback.onChunkTransferProgress(chunk, progress);
            }
        }

        chunk.setStatus("completed");
        if (callback != null) {
            callback.onChunkTransferComplete(chunk);
        }
    }

    private void transferViaUsb(FileChunk chunk) throws Exception {
        // Simulate USB transfer (faster than WiFi)
        chunk.setStatus("transferring");

        for (int progress = 0; progress <= 100; progress += 15) {
            Thread.sleep(80); // Simulate faster transfer
            chunk.setProgress(progress);

            if (callback != null) {
                callback.onChunkTransferProgress(chunk, progress);
            }
        }

        chunk.setStatus("completed");
        if (callback != null) {
            callback.onChunkTransferComplete(chunk);
        }
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
