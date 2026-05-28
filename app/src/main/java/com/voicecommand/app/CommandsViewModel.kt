package com.voicecommand.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicecommand.app.command.ActionType
import com.voicecommand.app.command.CommandGroupSummary
import com.voicecommand.app.command.CommandRepository
import com.voicecommand.app.command.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PresetType { NONE, SEARCH, MESSAGING, CALLING, PAYMENT, TIMER, LAUNCH, PLAY_SONG, NEXT_TRACK, PREVIOUS_TRACK, BACK_TRACK, DIRECTIONS, STOP_TRACK, START_TRACK }

class CommandsViewModel(application: Application) : AndroidViewModel(application) {

    val commandGroups: StateFlow<List<CommandGroupSummary>> =
        VoiceCommandApp.instance.commandRepository.allCommandGroups
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedCommand by mutableStateOf<CommandGroupSummary?>(null)
        private set

    fun selectCommand(command: CommandGroupSummary) {
        selectedCommand = command
    }

    fun clearSelection() {
        selectedCommand = null
    }

    private val repo get() = VoiceCommandApp.instance.commandRepository

    val availableActionTypes = listOf("LAUNCH_APP")

    val presetType: PresetType
        get() = when (editCommandId) {
            "browser_search" -> PresetType.SEARCH
            "messaging_preset" -> PresetType.MESSAGING
            "calling_preset" -> PresetType.CALLING
            "payment_preset" -> PresetType.PAYMENT
            "timer_preset" -> PresetType.TIMER
            "launch_preset"        -> PresetType.LAUNCH
            "play_song_preset"     -> PresetType.PLAY_SONG
            "next_track_preset"     -> PresetType.NEXT_TRACK
            "previous_track_preset" -> PresetType.PREVIOUS_TRACK
            "back_track_preset"     -> PresetType.BACK_TRACK
            "directions_preset"     -> PresetType.DIRECTIONS
            "stop_track_preset"     -> PresetType.STOP_TRACK
            "start_track_preset"    -> PresetType.START_TRACK
            else -> PresetType.NONE
        }

    private var cachedInstalledApps: List<InstalledApp>? = null

    val installedApps: List<InstalledApp>
        get() {
            if (cachedInstalledApps != null) return cachedInstalledApps!!
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            cachedInstalledApps = activities
                .mapNotNull { resolveInfo ->
                    try {
                        InstalledApp(
                            packageName = resolveInfo.activityInfo.packageName,
                            appName = resolveInfo.loadLabel(pm).toString()
                        )
                    } catch (_: Exception) { null }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName }
            return cachedInstalledApps!!
        }

    var showEditScreen by mutableStateOf(false)
        private set

    var isEditing by mutableStateOf(false)
        private set

    var editCommandId by mutableStateOf<String?>(null)
        private set

    var editLabel by mutableStateOf("")
    var editPhrases by mutableStateOf("")
    var editActionType by mutableStateOf("LAUNCH_APP")
    var editActionParam1 by mutableStateOf("")
    var editActionParam2 by mutableStateOf("")
    var editActionTargetName by mutableStateOf("")

    var editTriggerWord by mutableStateOf("")
    var editAppPackage by mutableStateOf("")

    var showAppPicker by mutableStateOf(false)
    var showAppCategoryPicker by mutableStateOf<String?>(null)

    private var cachedBrowserApps: List<InstalledApp>? = null
    private var cachedMessagingApps: List<InstalledApp>? = null
    private var cachedBankingApps: List<InstalledApp>? = null

    val browserApps: List<InstalledApp>
        get() {
            if (cachedBrowserApps != null) return cachedBrowserApps!!
            val pm = getApplication<Application>().packageManager
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
            val browserActivities = pm.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            val browserPackages = browserActivities.map { it.activityInfo.packageName }.toSet()
            cachedBrowserApps = installedApps.filter { it.packageName in browserPackages }
            return cachedBrowserApps!!
        }

    val messagingApps: List<InstalledApp>
        get() {
            if (cachedMessagingApps != null) return cachedMessagingApps!!
            val pm = getApplication<Application>().packageManager
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("sms:"))
            val smsActivities = pm.queryIntentActivities(smsIntent, PackageManager.MATCH_ALL)
            val smsPackages = smsActivities.map { it.activityInfo.packageName }.toSet()
            cachedMessagingApps = installedApps.filter { it.packageName in smsPackages }
            return cachedMessagingApps!!
        }

    val bankingApps: List<InstalledApp>
        get() {
            if (cachedBankingApps != null) return cachedBankingApps!!
            val pm = getApplication<Application>().packageManager
            val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            val upiActivities = pm.queryIntentActivities(upiIntent, PackageManager.MATCH_ALL)
            val upiPackages = upiActivities.map { it.activityInfo.packageName }.toSet()
            cachedBankingApps = installedApps.filter { it.packageName in upiPackages }
            return cachedBankingApps!!
        }

    private var cachedMusicApps: List<InstalledApp>? = null

    val musicApps: List<InstalledApp>
        get() {
            if (cachedMusicApps != null) return cachedMusicApps!!
            val pm = getApplication<Application>().packageManager
            val musicIntent = android.content.Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            val musicActivities = pm.queryIntentActivities(musicIntent, PackageManager.MATCH_ALL)
            val musicPackages = musicActivities.map { it.activityInfo.packageName }.toSet()
            cachedMusicApps = if (musicPackages.isNotEmpty()) {
                installedApps.filter { it.packageName in musicPackages }
            } else {
                installedApps // fall back to all apps if none specifically handle this intent
            }
            return cachedMusicApps!!
        }

    var showDeleteConfirm by mutableStateOf(false)
        private set

    var deleteTarget by mutableStateOf<CommandGroupSummary?>(null)
        private set

    fun showAddCommand() {
        editCommandId = null
        editLabel = ""
        editPhrases = ""
        editActionType = "LAUNCH_APP"
        editActionParam1 = ""
        editActionParam2 = ""
        editActionTargetName = ""
        editTriggerWord = ""
        editAppPackage = ""
        isEditing = false
        showEditScreen = true
    }

    fun showEditCommand(command: CommandGroupSummary) {
        editCommandId = command.commandId
        editLabel = command.displayLabel
        editPhrases = ""
        editActionType = command.actionType
        editTriggerWord = ""
        editAppPackage = ""
        isEditing = true
        showEditScreen = true

        if (presetType != PresetType.NONE) {
            editAppPackage = command.actionTarget
            val appName = when (presetType) {
                PresetType.SEARCH    -> browserApps.firstOrNull { it.packageName == command.actionTarget }?.appName
                PresetType.MESSAGING -> messagingApps.firstOrNull { it.packageName == command.actionTarget }?.appName
                PresetType.PAYMENT   -> bankingApps.firstOrNull { it.packageName == command.actionTarget }?.appName
                PresetType.PLAY_SONG -> musicApps.firstOrNull { it.packageName == command.actionTarget }?.appName
                else -> null
            }
            editActionTargetName = appName ?: ""
            viewModelScope.launch(Dispatchers.IO) {
                val phrases = repo.getPhrases(command.commandId)
                val default = when (presetType) {
                    PresetType.SEARCH    -> "search"
                    PresetType.MESSAGING -> "message"
                    PresetType.CALLING   -> "call"
                    PresetType.PAYMENT   -> "pay"
                    PresetType.TIMER         -> "set timer"
                    PresetType.LAUNCH        -> "launch"
                    PresetType.PLAY_SONG     -> "play"
                    PresetType.NEXT_TRACK     -> "next track"
                    PresetType.PREVIOUS_TRACK -> "replay"
                    PresetType.BACK_TRACK     -> "previous track"
                    PresetType.DIRECTIONS     -> "directions"
                    PresetType.STOP_TRACK     -> "stop"
                    PresetType.START_TRACK    -> "start"
                    else -> ""
                }
                withContext(Dispatchers.Main) {
                    editTriggerWord = phrases.firstOrNull() ?: default
                }
            }
            return
        }

        val params = CommandRepository.parseActionParams(command.actionType, command.actionTarget)
        editActionParam1 = when (ActionType.valueOf(command.actionType)) {
            ActionType.SEND_SMS -> params["number"] ?: ""
            ActionType.OPEN_URL -> params["url"] ?: ""
            ActionType.MAKE_CALL -> params["number"] ?: ""
            ActionType.SET_TIMER -> params["duration"] ?: ""
            ActionType.SET_ALARM -> params["time"] ?: ""
            ActionType.LAUNCH_APP -> command.actionTarget
            else -> params["toggle"] ?: ""
        }
        editActionParam2 = when (ActionType.valueOf(command.actionType)) {
            ActionType.SEND_SMS -> params["message"] ?: ""
            else -> ""
        }

        val appName = installedApps.firstOrNull { it.packageName == command.actionTarget }?.appName
        editActionTargetName = appName ?: command.actionTarget

        viewModelScope.launch(Dispatchers.IO) {
            val phrases = repo.getPhrases(command.commandId)
            val joined = phrases.joinToString("\n")
            withContext(Dispatchers.Main) {
                editPhrases = joined
            }
        }
    }

    fun hideEditScreen() {
        showEditScreen = false
        editCommandId = null
        isEditing = false
        editTriggerWord = ""
        editAppPackage = ""
    }

    fun saveCommand() {
        if (presetType == PresetType.SEARCH) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("browser_search", label, listOf(triggerWord), "LAUNCH_APP", editAppPackage)
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.MESSAGING) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("messaging_preset", label, listOf(triggerWord), "LAUNCH_APP", editAppPackage)
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.CALLING) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("calling_preset", label, listOf(triggerWord), "LAUNCH_APP", editAppPackage)
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.PAYMENT) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("payment_preset", label, listOf(triggerWord), "LAUNCH_APP", editAppPackage)
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.TIMER) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("timer_preset", label, listOf(triggerWord), "LAUNCH_APP", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.LAUNCH) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("launch_preset", label, listOf(triggerWord), "LAUNCH_APP", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.PLAY_SONG) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("play_song_preset", label, listOf(triggerWord), "LAUNCH_APP", editAppPackage)
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.NEXT_TRACK) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("next_track_preset", label, listOf(triggerWord), "MEDIA_NEXT", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.PREVIOUS_TRACK) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("previous_track_preset", label, listOf(triggerWord), "MEDIA_PREVIOUS", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.BACK_TRACK) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("back_track_preset", label, listOf(triggerWord), "MEDIA_PREVIOUS", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.DIRECTIONS) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("directions_preset", label, listOf(triggerWord), "NAVIGATE_TO", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.STOP_TRACK) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("stop_track_preset", label, listOf(triggerWord), "MEDIA_STOP", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        if (presetType == PresetType.START_TRACK) {
            val label = editLabel.trim()
            val triggerWord = editTriggerWord.trim()
            if (label.isBlank() || triggerWord.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                repo.updateCommand("start_track_preset", label, listOf(triggerWord), "MEDIA_START", "")
                withContext(Dispatchers.Main) { hideEditScreen() }
            }
            return
        }
        val label = editLabel.trim()
        val phrase = editPhrases.trim()
        if (label.isBlank() || phrase.isBlank()) return
        val requiresParam = editActionType !in listOf("TOGGLE_WIFI", "TOGGLE_BLUETOOTH")
        if (requiresParam && editActionParam1.isBlank()) return

        val actionTarget = CommandRepository.encodeActionParams(editActionType, editActionParam1, editActionParam2)

        viewModelScope.launch(Dispatchers.IO) {
            val existingIds = commandGroups.value.map { it.commandId }.toSet()
            val commandId = editCommandId ?: generateCommandId(label, existingIds)
            if (isEditing && editCommandId != null) {
                repo.updateCommand(commandId, label, listOf(phrase), editActionType, actionTarget)
            } else {
                repo.addCommand(commandId, label, listOf(phrase), editActionType, actionTarget)
            }
            withContext(Dispatchers.Main) {
                hideEditScreen()
            }
        }
    }

    fun requestDelete(command: CommandGroupSummary) {
        deleteTarget = command
        showDeleteConfirm = true
    }

    fun confirmDelete() {
        val target = deleteTarget ?: return
        if (target.commandId in setOf("browser_search", "messaging_preset", "calling_preset", "payment_preset", "timer_preset", "launch_preset", "play_song_preset", "next_track_preset", "previous_track_preset", "back_track_preset", "directions_preset", "stop_track_preset", "start_track_preset")) {
            cancelDelete()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCommand(target.commandId)
            withContext(Dispatchers.Main) {
                if (selectedCommand?.commandId == target.commandId) {
                    selectedCommand = null
                }
                cancelDelete()
            }
        }
    }

    fun cancelDelete() {
        showDeleteConfirm = false
        deleteTarget = null
    }

    private fun generateCommandId(label: String, existingIds: Set<String>): String {
        val base = label.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
            .ifBlank { "command" }
        var id = base
        var counter = 1
        while (id in existingIds) {
            id = "${base}_$counter"
            counter++
        }
        return id
    }
}
