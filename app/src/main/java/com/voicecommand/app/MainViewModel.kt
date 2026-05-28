package com.voicecommand.app

import android.app.AlarmManager
import android.app.Application
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicecommand.app.audio.AudioRecorder
import com.voicecommand.app.command.ActionExecutor
import com.voicecommand.app.command.Command
import com.voicecommand.app.command.CommandMatcher
import com.voicecommand.app.network.NetworkEngine
import com.voicecommand.app.audio.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    enum class UiState {
        LOADING,
        LISTENING,
        PROCESSING,
        EXECUTING,
        ERROR
    }

    var uiState by mutableStateOf(UiState.LOADING)
        private set

    var statusText by mutableStateOf("Loading speech model\u2026")
        private set

    var resultText by mutableStateOf("")
        private set

    var commandText by mutableStateOf<String?>(null)
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    var pendingCommandLabel by mutableStateOf<String?>(null)
        private set

    private var pendingCommand: Command? = null
    private var unlockReceiver: BroadcastReceiver? = null
    private var alarmRequestCode = 0

    var serverUrl by mutableStateOf("")
    var wakeWordServerUrl by mutableStateOf("")
    var isConnectedToServer by mutableStateOf(false)
        private set
    private var networkEngine: NetworkEngine? = null
    private val commandMatcher = CommandMatcher
    private val actionExecutor = ActionExecutor(VoiceCommandApp.instance)

    init {
        loadModel()
    }

    private fun scheduleAlarmActivity(ctx: Context, cmd: Command) {
        val app = getApplication<Application>()
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val targetIntent = buildTargetIntent(cmd)
        val intent = Intent(app, com.voicecommand.app.command.BridgeActivity::class.java).apply {
            putExtra(com.voicecommand.app.command.BridgeActivity.EXTRA_TARGET_INTENT, targetIntent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val requestCode = alarmRequestCode++
        val pendingIntent = PendingIntent.getActivity(
            app, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + 300L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun buildTargetIntent(cmd: Command): Intent? {
        val executor = actionExecutor
        return when (cmd.id) {
            "messaging_preset" -> {
                val text = cmd.actionParams["text"] ?: return null
                val (rawName, message) = com.voicecommand.app.command.ContactResolver.extractNameAndMessage(text)
                val contactNames = com.voicecommand.app.VoiceCommandApp.instance.contactNames
                val name = if (contactNames.isNotEmpty()) {
                    com.voicecommand.app.command.ContactResolver.findClosestContactName(rawName, contactNames) ?: rawName
                } else rawName
                val number = com.voicecommand.app.command.ContactResolver.findNumber(getApplication(), name) ?: return null
                val cleanNumber = number.replace(Regex("[^\\d+]"), "")
                val pkg = cmd.actionParams["package"]
                if (pkg == "com.whatsapp") {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}"))
                } else {
                    Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$number")).apply {
                        putExtra("sms_body", message)
                        pkg?.let { setPackage(it) }
                    }
                }
            }
            "calling_preset" -> {
                val name = cmd.actionParams["text"] ?: return null
                val number = com.voicecommand.app.command.ContactResolver.findNumber(getApplication(), name) ?: return null
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            }
            "payment_preset" -> {
                val rawName = cmd.actionParams["text"] ?: return null
                val amount = cmd.actionParams["amount"] ?: return null
                val contactNames = com.voicecommand.app.VoiceCommandApp.instance.contactNames
                val name = if (contactNames.isNotEmpty()) {
                    com.voicecommand.app.command.ContactResolver.findClosestContactName(rawName, contactNames) ?: rawName
                } else rawName
                val uri = Uri.parse("upi://pay?pn=${Uri.encode(name)}&am=$amount&cu=INR")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    cmd.actionParams["package"]?.let { setPackage(it) }
                }
            }
            "browser_search" -> {
                val query = cmd.actionParams["query"] ?: return null
                val url = "https://www.google.com/search?q=${Uri.encode(query)}"
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    cmd.actionParams["package"]?.let { setPackage(it) }
                }
            }
            else -> null
        }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<VoiceCommandApp>()

                val prefs = app.getSharedPreferences("config", 0)
                val savedUrl = prefs.getString("server_url", "") ?: ""
                serverUrl = savedUrl
                val savedWakeUrl = prefs.getString("wake_server_url", "") ?: ""
                wakeWordServerUrl = savedWakeUrl
                WakeWordService.serverToken = prefs.getString("server_token", "") ?: ""
                WakeWordService.serverAesKey = prefs.getString("server_aes_key", "") ?: ""

                if (savedUrl.isNotBlank()) {
                    val cleanUrl = savedUrl
                        .removePrefix("ws://").removePrefix("wss://")
                        .removePrefix("tcp://")
                    val host = cleanUrl.substringBefore(":")
                    val port = cleanUrl.substringAfter(":").toIntOrNull() ?: 8765

                    val engine = NetworkEngine(host, port)
                    val ok = engine.checkConnection()
                    if (ok) {
                        networkEngine = engine
                        withContext(Dispatchers.Main) { isConnectedToServer = true }
                    } else {
                        Log.w(TAG, "Server unreachable at $savedUrl, starting in offline mode")
                        withContext(Dispatchers.Main) { isConnectedToServer = false }
                    }
                } else {
                    Log.i(TAG, "No server URL configured, starting in offline mode")
                    withContext(Dispatchers.Main) { isConnectedToServer = false }
                }

                withContext(Dispatchers.Main) {
                    startWakeWordMode()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiState = UiState.ERROR
                    errorText = "Failed: ${e.message}"
                    statusText = "Connection failed"
                }
            }
        }
    }

    private fun startWakeWordMode() {
        val app = getApplication<VoiceCommandApp>()
        WakeWordService.onWakeWordDetected = {
            viewModelScope.launch(Dispatchers.Main) {
                onWakeWordTriggered()
            }
        }
        WakeWordService.onCommandAudio = { audioData ->
            viewModelScope.launch(Dispatchers.Main) {
                uiState = UiState.PROCESSING
                statusText = "Transcribing\u2026"
            }
            transcribeRecording(audioData)
        }
        val cleanWakeUrl = wakeWordServerUrl
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("tcp://")
        var host = cleanWakeUrl.substringBefore(":")
        var port = cleanWakeUrl.substringAfter(":").toIntOrNull() ?: 8766
        // If no dedicated wake-word server is configured, derive it from the main server
        if (host.isBlank()) {
            val cleanMainUrl = serverUrl
                .removePrefix("ws://").removePrefix("wss://")
                .removePrefix("tcp://")
            host = cleanMainUrl.substringBefore(":")
            port = 8766
        }
        val intent = Intent(app, WakeWordService::class.java).apply {
            putExtra("SERVER_HOST", host)
            putExtra("SERVER_PORT", port)
            putExtra("SERVER_TOKEN", WakeWordService.serverToken)
            putExtra("SERVER_AES_KEY", WakeWordService.serverAesKey)
        }
        app.startForegroundService(intent)
        uiState = UiState.LISTENING
        statusText = "Say 'Hi Gupta'\u2026"
    }

    fun updateServerUrl(url: String) {
        // Format: "host:port" (legacy) or "host:port|token|aeskey" (secure)
        val raw = url.trim().removePrefix("ws://").removePrefix("wss://").removePrefix("tcp://")
        val parts = raw.split("|")
        val clean = parts[0].trim()
        val token = if (parts.size >= 3) parts[1].trim() else ""
        val aesKey = if (parts.size >= 3) parts[2].trim() else ""
        serverUrl = clean
        WakeWordService.serverToken = token
        WakeWordService.serverAesKey = aesKey
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<VoiceCommandApp>()
            app.getSharedPreferences("config", 0).edit()
                .putString("server_url", clean)
                .putString("server_token", token)
                .putString("server_aes_key", aesKey)
                .apply()
            networkEngine?.close()
            networkEngine = null
            withContext(Dispatchers.Main) {
                uiState = UiState.LOADING
                statusText = "Reconnecting\u2026"
            }
            loadModel()
        }
    }

    fun updateWakeWordServerUrl(url: String) {
        val clean = url.trim()
            .removePrefix("ws://").removePrefix("wss://")
            .removePrefix("tcp://")
        wakeWordServerUrl = clean
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<VoiceCommandApp>()
            app.getSharedPreferences("config", 0)
                .edit().putString("wake_server_url", clean).apply()
        }
    }

    private var audioRecorder: AudioRecorder? = null

    fun onWakeWordTriggered() {
        if (uiState != UiState.LISTENING) return
        uiState = UiState.PROCESSING
        statusText = "Listening\u2026"
        resultText = ""
        commandText = null
        errorText = null
    }

    private fun playChime() {
        try {
            val sampleRate = 44100
            val durationSec = 0.3
            val numSamples = (sampleRate * durationSec).toInt()
            val samples = ShortArray(numSamples)
            val freq = 1047.0 // C6

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = if (i < numSamples / 6) {
                    i.toDouble() / (numSamples / 6)
                } else {
                    1.0 - (i - numSamples / 6).toDouble() / (numSamples * 5 / 6)
                }
                samples[i] = (envelope * 0.6 * 32767 * kotlin.math.sin(2.0 * Math.PI * freq * t)).toInt().toShort()
            }

            val track = android.media.AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
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
            Thread.sleep((durationSec * 1000).toLong() + 100)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.w("MainViewModel", "Chime failed: ${e.message}")
        }
    }

    private fun transcribeRecording(audioData: FloatArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            Log.d("Timing", "recorder stop took, audioData.size=${audioData.size}")
            if (audioData.isNotEmpty()) {
                var sum = 0.0; var maxV = 0.0f; var minV = 0.0f
                for (s in audioData) { sum += kotlin.math.abs(s.toDouble()); if (s > maxV) maxV = s; if (s < minV) minV = s }
                Log.d("AudioStats", "min=$minV max=$maxV mean=${sum/audioData.size} len=${audioData.size/16}ms")
            }

            if (audioData.isEmpty()) {
                withContext(Dispatchers.Main) {
                    uiState = UiState.LISTENING
                    statusText = "Say 'Hi Gupta'\u2026"
                    restartWakeWordListening()
                }
                return@launch
            }

            try {
                val engine = networkEngine

                val rawText: String
                if (engine != null) {
                    val t2 = System.currentTimeMillis()
                    rawText = engine.transcribe(audioData)
                    val t3 = System.currentTimeMillis()
                    Log.d("Timing", "network transcribe took ${t3 - t2}ms, text=\"$rawText\"")
                } else {
                    Log.d(TAG, "Server unreachable, transcribing locally")
                    val t2 = System.currentTimeMillis()
                    rawText = com.voicecommand.app.audio.WakeWordService.transcribeLocally(audioData) ?: ""
                    val t3 = System.currentTimeMillis()
                    Log.d("Timing", "local transcribe took ${t3 - t2}ms, text=\"$rawText\"")
                }

                withContext(Dispatchers.Main) {
                    if (rawText.isBlank()) {
                        errorText = "No speech detected. Please try again."
                        uiState = UiState.LISTENING
                        statusText = "Say 'Hi Gupta'\u2026"
                        restartWakeWordListening()
                    } else {
                        resultText = rawText
                        matchAndExecute(rawText)
                    }
                }
            } catch (e: Exception) {
                com.voicecommand.app.audio.WakeWordService.playBadChime()
                withContext(Dispatchers.Main) {
                    uiState = UiState.ERROR
                    errorText = "Transcription error: ${e.message}"
                    statusText = "Transcription failed"
                }
            }
        }
    }

    private fun matchAndExecute(transcript: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val matched = commandMatcher.match(transcript)
            val t1 = System.currentTimeMillis()
            Log.d("Timing", "command match took ${t1 - t0}ms, result=${matched?.id}")

            withContext(Dispatchers.Main) {
                if (matched != null) {
                    commandText = "Matched: ${matched.id}"
                    val noUnlockNeeded = matched.id in setOf(
                        "next_track_preset", "previous_track_preset", "back_track_preset",
                        "stop_track_preset", "start_track_preset"
                    )
                    if (isDeviceLocked() && !noUnlockNeeded) {
                        queueForUnlock(matched)
                    } else {
                        executeCommand(matched)
                    }
                } else {
                    playBadChimeAndReset()
                }
            }
        }
    }

    private fun isDeviceLocked(): Boolean {
        val km = getApplication<Application>().getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isDeviceLocked
    }

    private fun queueForUnlock(command: Command) {
        pendingCommand = command
        pendingCommandLabel = command.id
        statusText = "Queued: ${command.id} - unlock to execute"
        commandText = "Queued: ${command.id}"
        uiState = UiState.LISTENING
        registerUnlockReceiver()
        viewModelScope.launch {
            delay(3000L)
            commandText = null
            statusText = "Say 'Hi Gupta'\u2026"
            restartWakeWordListening()
        }
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val cmd = pendingCommand ?: return
                pendingCommand = null
                pendingCommandLabel = null
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                unlockReceiver = null
                viewModelScope.launch(Dispatchers.Main) {
                    executeCommand(cmd)
                }
            }
        }
        val app = getApplication<Application>()
        app.registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    private fun executeCommand(matched: Command) {
        uiState = UiState.EXECUTING
        statusText = "Executing ${matched.id}\u2026"
        viewModelScope.launch(Dispatchers.Main) {
            val result = actionExecutor.execute(matched)
            when (result) {
                is ActionExecutor.ExecutionResult.Success -> {
                    com.voicecommand.app.audio.WakeWordService.playGoodChime()
                    statusText = result.message
                    commandText = "Running ${matched.id}"
                }
                is ActionExecutor.ExecutionResult.Failed -> {
                    com.voicecommand.app.audio.WakeWordService.playBadChime()
                    errorText = result.message
                    commandText = "Failed: ${matched.id}"
                    statusText = "Command failed"
                }
            }
            uiState = UiState.LISTENING
            viewModelScope.launch {
                delay(3000L)
                commandText = null
                statusText = "Say 'Hi Gupta'\u2026"
                restartWakeWordListening()
            }
        }
    }

    private fun playBadChimeAndReset() {
        com.voicecommand.app.audio.WakeWordService.playBadChime()
        commandText = "No match found"
        statusText = "Try again"
        uiState = UiState.LISTENING
        viewModelScope.launch {
            delay(3000L)
            commandText = null
            restartWakeWordListening()
        }
    }

    private fun restartWakeWordListening() {
        try {
            val app = getApplication<VoiceCommandApp>()
            val intent = Intent(app, WakeWordService::class.java).apply {
                action = "RESTART"
            }
            app.startForegroundService(intent)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            unlockReceiver?.let { getApplication<Application>().unregisterReceiver(it) }
        } catch (_: Exception) {}
        unlockReceiver = null
        pendingCommand = null
        viewModelScope.launch {
            audioRecorder?.stop()
        }
        networkEngine?.close()
        try {
            getApplication<VoiceCommandApp>().stopService(
                Intent(getApplication(), WakeWordService::class.java)
            )
        } catch (_: Exception) {}
    }
}