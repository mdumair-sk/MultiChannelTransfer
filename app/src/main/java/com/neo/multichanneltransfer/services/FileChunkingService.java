package com.neo.multichanneltransfer.services;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.neo.multichanneltransfer.models.FileChunk;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;

public class FileChunkingService {
    private static final int DEFAULT_CHUNK_SIZE = 512 * 1024; // 512KB
    private final Context context;

    public FileChunkingService(Context context) {
        this.context = context;
    }

    public interface ProgressCallback {
        void onProgressUpdate(int percent);
    }

    public ArrayList<FileChunk> chunkFile(Uri fileUri, String fileName, ProgressCallback callback) throws Exception {
        ArrayList<FileChunk> chunks = new ArrayList<>();

        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
            if (inputStream == null) {
                throw new Exception("Cannot open file");
            }

            long totalSize = inputStream.available();
            byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;
            long bytesProcessed = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                String chunkId = fileName + "_chunk_" + chunkIndex;
                FileChunk chunk = new FileChunk(chunkId, chunkIndex, bytesRead);
                chunk.setData(chunkData);
                chunk.setChecksum(calculateChecksum(chunkData));

                chunks.add(chunk);
                chunkIndex++;

                bytesProcessed += bytesRead;
                if (callback != null && totalSize > 0) {
                    int percent = (int) ((bytesProcessed * 100) / totalSize);
                    callback.onProgressUpdate(percent);
                }
            }
        } catch (Exception e) {
            Log.e("FileChunkingService", "Error chunking file", e);
            throw e;
        }

        return chunks;
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            Log.e("FileChunkingService", "Checksum error", e);
            return "";
        }
    }
}
