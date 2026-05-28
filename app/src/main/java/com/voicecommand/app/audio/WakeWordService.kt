package com.voicecommand.app.audio

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WakeWordService : Service() {

    companion object {
        const val TAG = "WakeWordService"
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1001
        const val SAMPLE_RATE = 16000
        const val CHUNK_MS = 1000
        const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000
        const val HALF_CHUNK = SAMPLE_RATE * 500 / 1000
        const val WAKE_WORD = "gupta"
        const val VAD_SILENCE_MS = 400L
        const val VAD_THRESHOLD = 0.008f
        const val MAX_RECORD_MS = 5_000L

        var wakeWordHost = "192.168.1.100"
        var wakeWordPort = 8766

        /** Auth token (32 hex chars). Empty = no auth (legacy mode). */
        var serverToken = ""
        /** AES-256 key (64 hex chars). Empty = no encryption (legacy mode). */
        var serverAesKey = ""

        @Volatile
        var instance: WakeWordService? = null

        @Volatile
        var isListening = false
            private set

        @Volatile
        var keywordDetector: LocalKeywordDetector? = null

        var onWakeWordDetected: (() -> Unit)? = null
        var onCommandAudio: ((FloatArray) -> Unit)? = null

        private enum class State { SCANNING, RECORDING }

        fun transcribeLocally(audioData: FloatArray): String? {
            return keywordDetector?.transcribe(audioData)
        }

        fun playGoodChime() {
            try {
                val sampleRate = 44100
                val t1 = 120; val gap = 30; val t2 = 200
                val total = t1 + gap + t2
                val numSamples = (sampleRate * total / 1000).toInt()
                val samples = ShortArray(numSamples)
                val freq1 = 523.0; val freq2 = 659.0
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate * 1000
                    val envelope: Double
                    val freq: Double
                    when {
                        t < t1 -> { freq = freq1; envelope = t / t1 * (1.0 - t / t1 * 0.3) }
                        t < t1 + gap -> { freq = freq1; envelope = 0.0 }
                        else -> { freq = freq2; envelope = (1.0 - (t - t1 - gap) / t2) * 0.8 }
                    }
                    samples[i] = (envelope * 0.5 * 32767 * kotlin.math.sin(2.0 * Math.PI * freq * t / 1000.0)).toInt().toShort()
                }
                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, numSamples)
                track.setVolume(android.media.AudioTrack.getMaxVolume())
                track.play()
                Thread.sleep(total.toLong() + 50)
                track.stop(); track.release()
            } catch (_: Exception) {}
        }

        fun playBadChime() {
            try {
                val sampleRate = 44100; val durMs = 350
                val numSamples = (sampleRate * durMs / 1000).toInt()
                val samples = ShortArray(numSamples)
                val fStart = 400.0; val fEnd = 130.0
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate * 1000
                    val frac = t / durMs
                    val freq = fStart + (fEnd - fStart) * frac
                    val wobble = kotlin.math.sin(2.0 * Math.PI * 30.0 * t / 1000.0) * 0.3 + 1.0
                    val envelope = (1.0 - frac) * 0.9
                    samples[i] = (envelope * wobble * 32767 * kotlin.math.sin(2.0 * Math.PI * freq * t / 1000.0)).toInt().toShort()
                }
                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, numSamples)
                track.setVolume(android.media.AudioTrack.getMaxVolume())
                track.play()
                Thread.sleep(durMs.toLong() + 50)
                track.stop(); track.release()
            } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerHeadsetReceiver()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        scheduleKeepAlive()
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    when (intent.getIntExtra("state", -1)) {
                        1 -> {
                            // Wired headphones plugged in — music routes to ears, no need to duck
                            Log.d(TAG, "Wired headphones connected — releasing audio focus")
                            releaseAudioFocus()
                        }
                        0 -> {
                            // Wired headphones removed — handled by AUDIO_BECOMING_NOISY below
                        }
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Fired whenever audio output switches to speaker (wired OR Bluetooth removed)
                    Log.d(TAG, "Audio becoming noisy — requesting audio focus duck")
                    requestAudioFocus()
                }
            }
        }
    }

    private fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        registerReceiver(headsetReceiver, filter)
    }

    private fun scheduleKeepAlive() {
        val intent = Intent(this, WakeWordService::class.java).apply { action = "RESTART" }
        val pendingIntent = PendingIntent.getService(
            this, 10001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val interval = 5 * 60 * 1000L
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + interval,
            interval,
            pendingIntent
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "RESTART" -> {
                Log.d(TAG, "Restarting wake word listening")
                startListening()
                return START_STICKY
            }
        }
        // Pick up server credentials from intent extras
        intent?.getStringExtra("SERVER_HOST")?.let { wakeWordHost = it }
        intent?.getIntExtra("SERVER_PORT", wakeWordPort)?.let { wakeWordPort = it }
        intent?.getStringExtra("SERVER_TOKEN")?.let { serverToken = it }
        intent?.getStringExtra("SERVER_AES_KEY")?.let { serverAesKey = it }
        Log.d(TAG, "Starting wake word service (secure=${SecureChannel.isConfigured(serverToken, serverAesKey)})")
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        if (isListening) return
        isListening = true
        serverFailed = false

        // Duck music only when routing through the phone's speaker. If headphones are
        // connected the music never reaches the mic so ducking is unnecessary.
        // The headsetReceiver handles plug/unplug events during the session.
        if (isSpeakerActive()) requestAudioFocus()

        keywordDetector = LocalKeywordDetector(applicationContext)
        val localLoaded = keywordDetector!!.load()
        Log.d(TAG, "keywordDetector set to $keywordDetector")
        Log.d(TAG, "Local model loaded: $localLoaded")

        val warmupSilence = FloatArray(SAMPLE_RATE) { 0f }
        val warmupResult = keywordDetector!!.checkWakeWord(warmupSilence)
        Log.d(TAG, "Warm-up inference: \"$warmupResult\"")

        val debugDir = applicationContext.filesDir

        recordingJob = serviceScope.launch {
            val minBufferSize = maxOf(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                CHUNK_SAMPLES * 2
            )

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Audio permission denied", e)
                isListening = false
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                record.release()
                isListening = false
                return@launch
            }

            // Layer extra audio effects on top of the OS-level processing already applied
            // by VOICE_COMMUNICATION. AEC subtracts speaker output from the mic signal so
            // music bleed-through doesn't confuse the wake word model. NoiseSuppressor
            // further attenuates broadband background noise.
            val echoCanceler = if (AcousticEchoCanceler.isAvailable())
                AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
            else null
            val noiseSuppressor = if (NoiseSuppressor.isAvailable())
                NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
            else null
            Log.d(TAG, "AEC available=${AcousticEchoCanceler.isAvailable()} NS available=${NoiseSuppressor.isAvailable()}")

            val buf2 = ShortArray(HALF_CHUNK)
            var chunkIndex = 0
            var state = State.SCANNING

            val cmdBuffer = FloatArray(SAMPLE_RATE * 10) // 10s max
            var cmdFilled = 0
            var lastSpeechMs = 0L
            var hasSpeech = false
            var recordingStartMs = 0L

            val scanBuffer = FloatArray(CHUNK_SAMPLES) // rolling 1s buffer for wake word
            var scanFilled = 0

            record.startRecording()

            while (isActive && isListening) {
                val read = record.read(buf2, 0, HALF_CHUNK)
                if (read <= 0) continue

                val chunk = FloatArray(read) { buf2[it].toFloat() / 32768.0f }

                when (state) {
                    State.SCANNING -> {
                        // slide scan buffer, append new chunk
                        val shift = minOf(read, CHUNK_SAMPLES)
                        if (shift < CHUNK_SAMPLES) {
                            System.arraycopy(scanBuffer, shift, scanBuffer, 0, CHUNK_SAMPLES - shift)
                        }
                        System.arraycopy(chunk, 0, scanBuffer, CHUNK_SAMPLES - shift, minOf(read, CHUNK_SAMPLES))
                        scanFilled = minOf(scanFilled + read, CHUNK_SAMPLES)

                        chunkIndex++

                        if (chunkIndex == 3) {
                            val testResult = keywordDetector?.checkTestWav()
                            Log.d(TAG, "Model self-check full: \"$testResult\"")
                            val truncResult = keywordDetector?.checkTestWavTruncated(64000)
                            Log.d(TAG, "Model self-check truncated(64000): \"$truncResult\"")
                        }

                        var sum = 0f
                        for (v in chunk) sum += kotlin.math.abs(v)
                        val energy = sum / chunk.size

                        // Skip Whisper inference entirely when silent — this is the main
                        // cause of heating. Only run the AI model when voice is detected.
                        if (energy < VAD_THRESHOLD) continue

                        Log.d(TAG, "Processing 500ms chunk #$chunkIndex: energy=$energy")

                        val peak = scanBuffer.maxOf { kotlin.math.abs(it) }
                        val gain = if (peak > 0.1f) 1f else (0.1f / peak).coerceIn(1f, 60f)
                        val amplified = FloatArray(scanBuffer.size) { (scanBuffer[it] * gain).coerceIn(-1f, 1f) }

                        val text = keywordDetector?.checkWakeWord(amplified)
                        Log.d(TAG, "Local transcription (overlap): \"$text\"")
                        if (text != null && text.isNotBlank()) {
                            serviceScope.launch { playChime() }
                            onWakeWordDetected?.invoke()
                            Log.d(TAG, "WAKE WORD DETECTED (local): \"$text\"")

                            state = State.RECORDING
                            cmdFilled = 0
                            hasSpeech = false
                            recordingStartMs = System.currentTimeMillis()
                            lastSpeechMs = System.currentTimeMillis()
                        }

                        if (!serverFailed) {
                            val audioForServer = amplified
                            serviceScope.launch {
                                try {
                                    val sock = Socket()
                                    sock.connect(InetSocketAddress(wakeWordHost, wakeWordPort), 3000)
                                    sock.soTimeout = 5000
                                    val dos = sock.getOutputStream()

                                    // Build raw audio payload: [4 bytes sample count][float32 array]
                                    val plainBuf = ByteBuffer.allocate(4 + audioForServer.size * 4)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .putInt(audioForServer.size)
                                    for (s in audioForServer) plainBuf.putFloat(s)
                                    val plainBytes = plainBuf.array()

                                    val token = serverToken
                                    val aesKey = serverAesKey

                                    if (SecureChannel.isConfigured(token, aesKey)) {
                                        // Secure mode: [4B token len][token][4B enc len][IV+ciphertext+tag]
                                        val tokenBytes = token.toByteArray(Charsets.UTF_8)
                                        val encrypted = SecureChannel.encrypt(plainBytes, aesKey)
                                        val out = ByteBuffer.allocate(4 + tokenBytes.size + 4 + encrypted.size)
                                            .order(ByteOrder.LITTLE_ENDIAN)
                                        out.putInt(tokenBytes.size)
                                        out.put(tokenBytes)
                                        out.putInt(encrypted.size)
                                        out.put(encrypted)
                                        dos.write(out.array()); dos.flush()

                                        // Read encrypted response: [4B enc len][IV+ciphertext+tag]
                                        val dis = java.io.DataInputStream(sock.getInputStream())
                                        val encLenBytes = ByteArray(4); dis.readFully(encLenBytes)
                                        val encLen = ByteBuffer.wrap(encLenBytes).order(ByteOrder.LITTLE_ENDIAN).int
                                        val encResp = ByteArray(encLen); dis.readFully(encResp)
                                        sock.close()
                                        val decrypted = SecureChannel.decrypt(encResp, aesKey)
                                        val textLen = ByteBuffer.wrap(decrypted, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                        val serverText = String(decrypted, 4, textLen, Charsets.UTF_8).trim().lowercase()
                                        Log.d(TAG, "Wake check (server, secure): \"$serverText\"")
                                    } else {
                                        // Legacy mode: plain TCP (no token/key configured)
                                        dos.write(plainBytes); dos.flush()
                                        val dis = java.io.DataInputStream(sock.getInputStream())
                                        val lenBytes = ByteArray(4); dis.readFully(lenBytes)
                                        val textLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
                                        val textBytes = ByteArray(textLen); dis.readFully(textBytes)
                                        sock.close()
                                        val serverText = String(textBytes, Charsets.UTF_8).trim().lowercase()
                                        Log.d(TAG, "Wake check (server, plain): \"$serverText\"")
                                    }
                                } catch (_: Exception) { serverFailed = true }
                            }
                        }
                    }

                    State.RECORDING -> {
                        val elapsed = System.currentTimeMillis() - recordingStartMs
                        if (elapsed > MAX_RECORD_MS) {
                            val cmdCb = onCommandAudio
                            if (cmdCb != null && cmdFilled > 0) {
                                Log.d(TAG, "RECORDING timeout, sending ${cmdFilled} samples")
                                val rawAudio = cmdBuffer.copyOfRange(0, cmdFilled)
                                val meanVal = rawAudio.sum() / rawAudio.size
                                for (i in rawAudio.indices) rawAudio[i] -= meanVal
                                serviceScope.launch { cmdCb(rawAudio) }
                            } else {
                                Log.d(TAG, "RECORDING timeout, discarding empty buffer")
                            }
                            state = State.SCANNING
                            continue
                        }

                        val toCopy = minOf(read, cmdBuffer.size - cmdFilled)
                        System.arraycopy(chunk, 0, cmdBuffer, cmdFilled, toCopy)
                        cmdFilled += toCopy

                        var energy = 0f
                        for (i in 0 until read) energy += kotlin.math.abs(buf2[i].toFloat())
                        energy /= (read * 32768f)

                        Log.d(TAG, "RECORDING: filled=${cmdFilled} energy=$energy hasSpeech=$hasSpeech")

                        if (energy > VAD_THRESHOLD) {
                            lastSpeechMs = System.currentTimeMillis()
                            if (!hasSpeech) {
                                hasSpeech = true
                                Log.d(TAG, "RECORDING: speech detected")
                            }
                        }

                        if (hasSpeech && (System.currentTimeMillis() - lastSpeechMs) > VAD_SILENCE_MS) {
                            Log.d(TAG, "VAD: silence for ${VAD_SILENCE_MS}ms, stopping")
                            val rawAudio = cmdBuffer.copyOfRange(0, cmdFilled)

                            val meanVal = rawAudio.sum() / rawAudio.size
                            for (i in rawAudio.indices) rawAudio[i] -= meanVal

                            val cmdCb = onCommandAudio
                            if (cmdCb != null) {
                                serviceScope.launch { cmdCb(rawAudio) }
                            }

                            state = State.SCANNING
                        }
                    }
                }
            }

            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) {}
            echoCanceler?.release()
            noiseSuppressor?.release()
            record.release()
            isListening = false
        }
    }

    private var serverFailed = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    /**
     * True when audio is routing through the phone's built-in speaker — i.e. no wired or
     * Bluetooth headphones are connected. Uses stable APIs that need no extra permissions.
     * ACTION_AUDIO_BECOMING_NOISY handles the Bluetooth disconnect case at runtime.
     */
    private fun isSpeakerActive(): Boolean {
        val am = audioManager ?: return true
        @Suppress("DEPRECATION")
        return try {
            !am.isWiredHeadsetOn && !am.isBluetoothA2dpOn
        } catch (_: Exception) {
            !am.isWiredHeadsetOn  // Fallback: wired-only check
        }
    }

    /**
     * Requests transient audio focus so music apps lower their volume while the user
     * speaks the command. Only called when music is routing through the phone speaker.
     */
    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build())
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener {}
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    /** Returns audio focus so music resumes normal volume after the command is captured. */
    private fun releaseAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private fun playChime() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        } catch (_: Exception) {}

        try {
            val sampleRate = 44100
            val durationSec = 0.4
            val numSamples = (sampleRate * durationSec).toInt()
            val samples = ShortArray(numSamples)

            val freq = 1047.0 // C6

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = if (i < numSamples / 8) {
                    i.toDouble() / (numSamples / 8)
                } else {
                    1.0 - (i - numSamples / 8).toDouble() / (numSamples * 7 / 8)
                }
                samples[i] = (envelope * 0.9 * 32767 * kotlin.math.sin(2.0 * Math.PI * freq * t)).toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, numSamples)
            track.setVolume(AudioTrack.getMaxVolume())
            track.play()

            Thread.sleep((durationSec * 1000).toLong() + 200)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.w(TAG, "Chime failed: ${e.message}")
        }
    }

    fun stopListening() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioFocus()
        try { unregisterReceiver(headsetReceiver) } catch (_: Exception) {}
        keywordDetector?.release()
        keywordDetector = null
    }

    override fun onDestroy() {
        instance = null
        stopListening()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listening for wake word"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Command")
            .setContentText("Listening for \"GUPTA\"...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        return builder.build()
    }
}