package com.example.dialermobilepoc

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dialermobilepoc.model.CallLog
import com.example.dialermobilepoc.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.net.URI

private const val TAG = "RecordingPlayback"

class CallLogsActivity : AppCompatActivity() {

    private lateinit var adapter: CallLogAdapter
    private lateinit var rootView: View
    private var pollingJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playingUrl: String? = null
    private var pendingDownload: Pair<CallLog, (Boolean) -> Unit>? = null

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingDownload
            pendingDownload = null
            if (pending == null) return@registerForActivityResult
            val (callLog, onResult) = pending
            if (granted) {
                performDownload(callLog, onResult)
            } else {
                Toast.makeText(
                    this,
                    "Download needs storage permission on this Android version.",
                    Toast.LENGTH_LONG
                ).show()
                onResult(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_call_logs)
        rootView = findViewById(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recyclerView = findViewById<RecyclerView>(R.id.callLogsRecyclerView)
        adapter = CallLogAdapter(
            onPlayClick = { callLog -> onPlayClicked(callLog) },
            onCheckRecordingClick = { callLog -> onCheckRecordingClicked(callLog) },
            onDownloadClick = { callLog, onResult -> onDownloadClicked(callLog, onResult) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.scrollToBottomButton).setOnClickListener {
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }

        findViewById<Button>(R.id.scrollToTopButton).setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun startPolling() {
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                fetchCalls()
                delay(4000)
            }
        }
    }

    private suspend fun fetchCalls() {
        try {
            val calls = RetrofitClient.api.getCalls().sortedByDescending { it.id }
            adapter.submitList(calls)
        } catch (e: Exception) {
            // Network hiccup - skip this cycle and keep showing the last known list.
        }
    }

    private fun onCheckRecordingClicked(callLog: CallLog) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.checkRecording(callLog.id)
                val recordingUrl = if (response.isSuccessful) response.body()?.recordingUrl else null
                fetchCalls()
                if (recordingUrl.isNullOrBlank()) {
                    Toast.makeText(this@CallLogsActivity, "No recording found yet — try again shortly.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CallLogsActivity, "No recording found yet — try again shortly.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onDownloadClicked(callLog: CallLog, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = callLog to onResult
                storagePermissionLauncher.launch(permission)
                return
            }
        }
        performDownload(callLog, onResult)
    }

    private fun performDownload(callLog: CallLog, onResult: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.downloadRecordingLocal(callLog.id)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val fileName = "call_${callLog.id}.mp3"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val savedUri = saveToDownloadsMediaStore(fileName, body)
                        if (savedUri != null) {
                            showDownloadSuccess(fileName, savedUri)
                            onResult(true)
                        } else {
                            showDownloadFailure()
                            onResult(false)
                        }
                    } else {
                        val saved = saveToDownloadsLegacy(fileName, body)
                        if (saved) {
                            showDownloadSuccess(fileName, null)
                            onResult(true)
                        } else {
                            showDownloadFailure()
                            onResult(false)
                        }
                    }
                } else {
                    showDownloadFailure()
                    onResult(false)
                }
            } catch (e: Exception) {
                showDownloadFailure()
                onResult(false)
            }
        }
    }

    private fun showDownloadFailure() {
        Toast.makeText(
            this@CallLogsActivity,
            "Recording not available locally yet — try Check Recording first, or try again shortly.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showDownloadSuccess(fileName: String, savedUri: Uri?) {
        Snackbar.make(rootView, "Saved to Downloads: $fileName", Snackbar.LENGTH_LONG)
            .setAction("OPEN") { openDownloadedFile(savedUri) }
            .show()
    }

    private fun openDownloadedFile(savedUri: Uri?) {
        if (savedUri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(savedUri, "audio/mpeg")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // no app can open the file directly - fall back to the Downloads folder below
            }
        }
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Open your Files app > Downloads to find the file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToDownloadsMediaStore(fileName: String, body: ResponseBody): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = contentResolver.openOutputStream(uri)?.use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
            true
        } ?: false
        if (!written) {
            contentResolver.delete(uri, null, null)
            return null
        }
        return uri
    }

    private fun saveToDownloadsLegacy(fileName: String, body: ResponseBody): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
        }
        return true
    }

    private fun onPlayClicked(callLog: CallLog) {
        val rawUrl = callLog.recordingUrl
        if (rawUrl.isNullOrBlank()) return

        if (playingUrl == rawUrl) {
            stopPlayback()
            return
        }

        stopPlayback()

        try {
            val resolvedUrl = resolveRecordingUrl(rawUrl)
            Log.d(TAG, resolvedUrl)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(resolvedUrl)
                setOnPreparedListener { start() }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "what=$what extra=$extra")
                    Toast.makeText(this@CallLogsActivity, "Playback failed", Toast.LENGTH_SHORT).show()
                    stopPlayback()
                    true
                }
                prepareAsync()
            }
            playingUrl = rawUrl
            adapter.setPlayingUrl(rawUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Playback setup failed: ${e.message}")
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show()
            stopPlayback()
        }
    }

    // recordingUrl from the backend looks like "http://localhost:8080/api/recordings/{id}",
    // which is unreachable from the emulator - swap in RetrofitClient's host instead.
    private fun resolveRecordingUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)
            val path = uri.rawPath ?: return rawUrl
            val query = uri.rawQuery
            val base = RetrofitClient.BASE_URL.trimEnd('/')
            base + path + (if (!query.isNullOrBlank()) "?$query" else "")
        } catch (e: Exception) {
            rawUrl
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let {
            try {
                it.release()
            } catch (e: Exception) {
                // ignore - already released or invalid state
            }
        }
        mediaPlayer = null
        playingUrl = null
        if (::adapter.isInitialized) {
            adapter.setPlayingUrl(null)
        }
    }
}