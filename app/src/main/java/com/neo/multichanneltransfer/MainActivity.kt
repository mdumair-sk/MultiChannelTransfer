package com.neo.multichanneltransfer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.neo.multichanneltransfer.managers.LoadBalancer
import com.neo.multichanneltransfer.models.*
import com.neo.multichanneltransfer.services.ConnectionService
import com.neo.multichanneltransfer.services.FileChunkingService
import com.neo.multichanneltransfer.services.TransferService
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), TransferService.TransferCallback {

    companion object {
        private const val TAG = "MultiChannelTransfer"
        private const val DEFAULT_CHUNK_SIZE = 512 * 1024 // 512KB
        private const val DEFAULT_PC_IP = "192.168.1.11"
        private const val DEFAULT_PORT = 8765
    }

    private lateinit var selectFileButton: Button
    private lateinit var startTransferButton: Button
    private lateinit var pcIpEditText: EditText
    private lateinit var chunkSizeEditText: EditText

    private lateinit var selectedFileText: TextView
    private lateinit var transferProgressText: TextView
    private lateinit var transferSpeedText: TextView
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var progressBar: ProgressBar

    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileSize: Long = 0

    private val connectionService by lazy { ConnectionService(this) }
    private val fileChunkingService by lazy { FileChunkingService(this) }
    private val transferService by lazy { TransferService() }
    private val loadBalancer = LoadBalancer()

    private var chunks: List<FileChunk> = emptyList()
    private var startTimeMillis = 0L

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Permissions are required for file selection and transfer.", Toast.LENGTH_LONG).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        checkAndRequestPermissions()
        setupListeners()
        logMessage("App initialized")
    }

    private fun initializeViews() {
        selectFileButton = findViewById(R.id.selectFileButton)
        startTransferButton = findViewById(R.id.startTransferButton)
        pcIpEditText = findViewById(R.id.pcIpEditText)
        chunkSizeEditText = findViewById(R.id.chunkSizeEditText)

        selectedFileText = findViewById(R.id.selectedFileText)
        transferProgressText = findViewById(R.id.transferProgressText)
        transferSpeedText = findViewById(R.id.transferSpeedText)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        progressBar = findViewById(R.id.progressBar)

        pcIpEditText.setText(DEFAULT_PC_IP)
        chunkSizeEditText.setText((DEFAULT_CHUNK_SIZE / 1024).toString())
        startTransferButton.isEnabled = false
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupListeners() {
        selectFileButton.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        startTransferButton.setOnClickListener {
            startFileTransfer()
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        selectedFileUri = uri
        lifecycleScope.launch(Dispatchers.IO) {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    selectedFileName = it.getString(nameIndex) ?: "Unknown"
                    selectedFileSize = it.getLong(sizeIndex)
                }
            }
            withContext(Dispatchers.Main) {
                selectedFileText.text = "Selected: $selectedFileName (${formatFileSize(selectedFileSize)})"
                logMessage("File selected: $selectedFileName, Size: ${formatFileSize(selectedFileSize)}")
                startTransferButton.isEnabled = true
            }
        }
    }

    private fun startFileTransfer() {
        val uri = selectedFileUri ?: run {
            Toast.makeText(this, "Please select a file first.", Toast.LENGTH_SHORT).show()
            return
        }

        val pcIp = pcIpEditText.text.toString().trim()
        if (pcIp.isEmpty()) {
            Toast.makeText(this, "Please enter PC IP address.", Toast.LENGTH_SHORT).show()
            return
        }

        val chunkSizeKB = chunkSizeEditText.text.toString().toIntOrNull() ?: (DEFAULT_CHUNK_SIZE / 1024)
        val chunkSizeBytes = (chunkSizeKB * 1024).coerceAtLeast(64 * 1024) // minimum 64 KB

        logMessage("Starting chunking file...")
        startTransferButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        transferProgressText.text = "Preparing..."

        lifecycleScope.launch {
            try {
                chunks = fileChunkingService.chunkFile(uri, selectedFileName, chunkSizeBytes) { percent ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        transferProgressText.text = "Chunking progress: $percent%"
                    }
                }
                logMessage("Chunking completed: ${chunks.size} chunks")
                startPersistentTransfer(pcIp, DEFAULT_PORT)
            } catch (e: Exception) {
                logMessage("Chunking failed: ${e.message}")
                startTransferButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun startPersistentTransfer(pcIp: String, port: Int) = withContext(Dispatchers.Main) {
        connectionService.detectConnections()
        val connections = connectionService.getAllConnections()
        loadBalancer.distributeChunks(chunks, connections)

        transferService.startTransferSession(
            pcIp = pcIp,
            port = port,
            transferId = UUID.randomUUID().toString(),
            fileName = selectedFileName,
            fileSize = selectedFileSize,
            totalChunks = chunks.size,
            callback = this@MainActivity
        )

        // Enqueue all chunks by assigned channel
        for (chunk in chunks) {
            transferService.enqueueChunk(chunk)
        }

        val totalChunks = chunks.size
        var completedChunks = 0
        progressBar.max = 100
        progressBar.progress = 0
        startTimeMillis = System.currentTimeMillis()

        // Monitor completion asynchronously
        lifecycleScope.launch(Dispatchers.Default) {
            while (completedChunks < totalChunks) {
                completedChunks = chunks.count { it.status == ChunkStatus.COMPLETED }
                val progressPercent = ((completedChunks * 100).toDouble() / totalChunks).toInt()
                withContext(Dispatchers.Main) {
                    transferProgressText.text = "Transfer progress: $completedChunks / $totalChunks ($progressPercent%)"
                    progressBar.progress = progressPercent
                }
                delay(200)
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                startTransferButton.isEnabled = true
                logMessage("File transfer completed successfully!")
                Toast.makeText(this@MainActivity, "File transfer completed!", Toast.LENGTH_LONG).show()
                // Optionally: transferService.closeConnections()
            }
        }
    }

    override fun onChunkTransferComplete(chunk: FileChunk) {
        logMessage("Chunk ${chunk.chunkIndex} transferred successfully.")
    }

    override fun onChunkTransferProgress(chunk: FileChunk, progress: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            transferProgressText.text = "Chunk ${chunk.chunkIndex} progress: $progress%"
        }
    }

    override fun onChunkTransferError(chunk: FileChunk, error: String) {
        logMessage("Chunk ${chunk.chunkIndex} transfer error: $error")
    }

    private fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        logTextView.append("$logEntry\n")
        logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
        Log.d(TAG, message)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.US, "%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
