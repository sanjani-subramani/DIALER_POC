package com.example.dialerrecorderpoc

import android.Manifest
import com.google.firebase.messaging.FirebaseMessaging
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var numberInput: EditText
    private lateinit var dialButton: Button
    private lateinit var playButton: Button
    private lateinit var statusText: TextView

    private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var lastFile: File? = null
    private var usedSource: String = "none"

    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                if (state == lastState) return
                lastState = state
                when (state) {
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        log(">>> OFFHOOK — call ACTIVE → starting recording")
                        startRecording()
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        log("<<< IDLE — call ENDED → stopping recording")
                        stopRecording()
                    }
                }
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val ok = result.values.all { it }
            log("Permissions granted: $ok")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        numberInput = findViewById(R.id.numberInput)
        dialButton = findViewById(R.id.dialButton)
        playButton = findViewById(R.id.playButton)
        statusText = findViewById(R.id.statusText)

        registerReceiver(callStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
        log("Listening for call-state changes...")
        // Fetch this device's FCM token and show it in the log
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                log("FCM TOKEN: $token")
                android.util.Log.d("FCM", "Token: $token")
            } else {
                log("FCM token fetch failed: ${task.exception?.message}")
            }
        }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECORD_AUDIO
            )
        )

        dialButton.setOnClickListener {
            val number = numberInput.text.toString().trim()
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a phone number first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED
            ) placeCall(number)
            else permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }

        playButton.setOnClickListener { playLast() }
    }

    private fun startRecording() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            log("ERROR: RECORD_AUDIO not granted")
            return
        }
        // Save into the app's own folder — no storage permission needed on Android 13
        val dir = getExternalFilesDir(null)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "call_$stamp.m4a")
        lastFile = file

        // Try VOICE_CALL first (Samsung usually blocks it), fall back to MIC
        if (tryStart(MediaRecorder.AudioSource.VOICE_CALL, "VOICE_CALL", file)) return
        if (tryStart(MediaRecorder.AudioSource.MIC, "MIC", file)) return
        log("ALL audio sources FAILED — recording not possible on this device")
    }

    private fun tryStart(source: Int, name: String, file: File): Boolean {
        return try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            isRecording = true
            usedSource = name
            log("Recording STARTED using source: $name")
            true
        } catch (e: Exception) {
            log("Source $name failed: ${e.javaClass.simpleName} — ${e.message}")
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            false
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
            log("Recording STOPPED (source was: $usedSource)")
        } catch (e: Exception) {
            log("Stop error: ${e.message}")
        } finally {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
        }
        val f = lastFile
        if (f != null && f.exists()) {
            val kb = f.length() / 1024
            log("FILE: ${f.name}  |  size: $kb KB")
            log("PATH: ${f.absolutePath}")
            if (kb < 5) log("WARNING: file is tiny — likely silent/blocked")
        } else {
            log("ERROR: no file was created")
        }
    }

    private fun playLast() {
        val f = lastFile
        if (f == null || !f.exists()) {
            log("No recording to play yet")
            return
        }
        try {
            val mp = MediaPlayer()
            mp.setDataSource(f.absolutePath)
            mp.prepare()
            mp.start()
            log("Playing ${f.name} ... LISTEN: whose voice do you hear?")
            mp.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            log("Play error: ${e.message}")
        }
    }

    private fun placeCall(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$number") })
            log("Placing call to $number ...")
        } catch (e: Exception) {
            log("Error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(callStateReceiver) } catch (_: IllegalArgumentException) {}
        try { recorder?.release() } catch (_: Exception) {}
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread { statusText.text = "[$time] $message\n${statusText.text}" }
    }
}