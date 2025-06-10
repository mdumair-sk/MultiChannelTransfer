package com.neo.multichanneltransfer;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;

import com.neo.multichanneltransfer.models.FileChunk;
import com.neo.multichanneltransfer.services.FileChunkingService;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class FileChunkingServiceTest {

    private Context context;
    private File testFile;
    private Uri testUri;
    private FileChunkingService service;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        service = new FileChunkingService(context);

        // Create a test file with 1MB data
        testFile = new File(context.getCacheDir(), "test_file.txt");
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            byte[] data = new byte[1024 * 1024]; // 1MB
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
            fos.write(data);
        }

        testUri = Uri.fromFile(testFile);
    }

    @Test
    public void testChunkingSplitsCorrectly() throws Exception {
        ArrayList<FileChunk> chunks = service.chunkFile(testUri, "test", null);

        // 1MB file should split into exactly 2 chunks (512KB each)
        assertEquals(2, chunks.size());

        for (FileChunk chunk : chunks) {
            assertNotNull(chunk.getChecksum());
            assertNotNull(chunk.getData());
            assertTrue(chunk.getSize() > 0);
        }
    }

    @Test
    public void testSmallFileHandling() throws Exception {
        // Create a very small test file (100 bytes)
        File smallFile = new File(context.getCacheDir(), "small.txt");
        try (FileOutputStream fos = new FileOutputStream(smallFile)) {
            fos.write("Hello World!".getBytes());
        }

        Uri smallUri = Uri.fromFile(smallFile);
        ArrayList<FileChunk> chunks = service.chunkFile(smallUri, "small", null);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getSize() <= 512 * 1024);
    }

    @Test
    public void testProgressCallback() throws Exception {
        final int[] lastProgress = {0};

        ArrayList<FileChunk> chunks = service.chunkFile(testUri, "test", percent -> {
            lastProgress[0] = percent;
        });

        assertEquals(2, chunks.size());
        assertTrue(lastProgress[0] >= 100);
    }
}
