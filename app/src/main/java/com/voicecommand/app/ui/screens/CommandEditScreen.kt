package com.voicecommand.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicecommand.app.PresetType
import com.voicecommand.app.command.ActionType
import com.voicecommand.app.command.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandEditScreen(
    label: String,
    onLabelChange: (String) -> Unit,
    phrases: String,
    onPhrasesChange: (String) -> Unit,
    actionType: String,
    onActionTypeChange: (String) -> Unit,
    actionParam1: String,
    onActionParam1Change: (String) -> Unit,
    actionParam2: String,
    onActionParam2Change: (String) -> Unit,
    actionTargetName: String,
    onPickApp: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    isEditing: Boolean,
    saveEnabled: Boolean,
    availableActionTypes: List<String>,
    showAppPicker: Boolean,
    presetType: PresetType = PresetType.NONE,
    triggerWord: String = "",
    onTriggerWordChange: (String) -> Unit = {},
    onPickAppCategory: (String) -> Unit = {},
    editAppPackage: String = ""
) {
    val isPreset = presetType != PresetType.NONE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Command" else "Add Command",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Spacer(modifier = Modifier.height(8.dp))

            // ── Display Label ─────────────────────────────────────────────────
            SectionLabel("Display Label")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                placeholder = { Text("e.g. Open Maps") },
                readOnly = isEditing,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Trigger ───────────────────────────────────────────────────────
            if (isPreset) {
                SectionLabel("Trigger Word")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = triggerWord,
                    onValueChange = onTriggerWordChange,
                    placeholder = {
                        Text(
                            when (presetType) {
                                PresetType.SEARCH         -> "search"
                                PresetType.MESSAGING      -> "message"
                                PresetType.CALLING        -> "call"
                                PresetType.PAYMENT        -> "pay"
                                PresetType.TIMER          -> "set timer"
                                PresetType.LAUNCH         -> "launch"
                                PresetType.PLAY_SONG      -> "play"
                                PresetType.NEXT_TRACK     -> "next track"
                                PresetType.PREVIOUS_TRACK -> "replay"
                                PresetType.BACK_TRACK     -> "previous track"
                                PresetType.DIRECTIONS     -> "navigate to"
                                PresetType.STOP_TRACK     -> "stop"
                                PresetType.START_TRACK    -> "start"
                                else                      -> ""
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (presetType) {
                        PresetType.SEARCH         -> "Say this word followed by your search query."
                        PresetType.MESSAGING      -> "Say this word followed by contact name and message."
                        PresetType.CALLING        -> "Say this word followed by a contact name."
                        PresetType.PAYMENT        -> "Say this word followed by name and amount."
                        PresetType.TIMER          -> "Say this word followed by the number of minutes."
                        PresetType.LAUNCH         -> "Say this word followed by any app name — Gupta will find the best match automatically."
                        PresetType.PLAY_SONG      -> "Say this word followed by a song name."
                        PresetType.NEXT_TRACK     -> "Say this phrase to skip to the next song."
                        PresetType.PREVIOUS_TRACK -> "Say this phrase to restart the current song from the beginning."
                        PresetType.BACK_TRACK     -> "Say this phrase to go back to the previous song."
                        PresetType.DIRECTIONS     -> "Say this word followed by a place name — Gupta will open Maps navigation."
                        PresetType.STOP_TRACK     -> "Say this word to stop music playback."
                        PresetType.START_TRACK    -> "Say this word to resume music playback."
                        else                      -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SectionLabel("Trigger Phrase")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = phrases,
                    onValueChange = onPhrasesChange,
                    placeholder = { Text("e.g. open maps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "One phrase per line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Non-preset: action type + target ─────────────────────────────
            if (!isPreset) {
                Spacer(modifier = Modifier.height(20.dp))
                ActionTypeDropdown(
                    selected = actionType,
                    options = availableActionTypes,
                    onSelect = onActionTypeChange
                )
                Spacer(modifier = Modifier.height(20.dp))
                ActionTargetFields(
                    actionType = actionType,
                    param1 = actionParam1,
                    onParam1Change = onActionParam1Change,
                    param2 = actionParam2,
                    onParam2Change = onActionParam2Change,
                    targetName = actionTargetName,
                    onPickApp = onPickApp
                )
            } else if (presetType != PresetType.CALLING && presetType != PresetType.TIMER && presetType != PresetType.LAUNCH && presetType != PresetType.NEXT_TRACK && presetType != PresetType.PREVIOUS_TRACK && presetType != PresetType.BACK_TRACK && presetType != PresetType.DIRECTIONS && presetType != PresetType.STOP_TRACK && presetType != PresetType.START_TRACK && presetType != PresetType.NONE) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(
                    when (presetType) {
                        PresetType.SEARCH    -> "Browser App"
                        PresetType.MESSAGING -> "Messaging App"
                        PresetType.PAYMENT   -> "Banking App"
                        PresetType.PLAY_SONG -> "Music App"
                        else                 -> "App"
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                AppPickerRow(
                    label = if (editAppPackage.isNotEmpty()) actionTargetName
                    else "Auto-select ${
                        when (presetType) {
                            PresetType.SEARCH    -> "default browser"
                            PresetType.MESSAGING -> "messaging app"
                            PresetType.PAYMENT   -> "UPI app"
                            PresetType.PLAY_SONG -> "music app"
                            else                 -> "app"
                        }
                    }",
                    selected = editAppPackage.isNotEmpty(),
                    onClick = {
                        onPickAppCategory(
                            when (presetType) {
                                PresetType.SEARCH    -> "browser"
                                PresetType.MESSAGING -> "messaging"
                                PresetType.PAYMENT   -> "banking"
                                PresetType.PLAY_SONG -> "music"
                                else                 -> ""
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (presetType) {
                        PresetType.SEARCH    -> "Leave empty to use your default browser."
                        PresetType.MESSAGING -> "Choose a specific messaging app or leave empty for SMS."
                        PresetType.PAYMENT   -> "Leave empty to auto-select a UPI app."
                        PresetType.PLAY_SONG -> "Choose your preferred music app (Spotify, YouTube Music, etc.)."
                        else                 -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Save button ───────────────────────────────────────────────────
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Add Command",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AppPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypeDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SectionLabel("Action Type")
    Spacer(modifier = Modifier.height(6.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = formatActionType(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { type ->
                DropdownMenuItem(
                    text = { Text(formatActionType(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionTargetFields(
    actionType: String,
    param1: String,
    onParam1Change: (String) -> Unit,
    param2: String,
    onParam2Change: (String) -> Unit,
    targetName: String,
    onPickApp: () -> Unit
) {
    when (ActionType.valueOf(actionType)) {
        ActionType.LAUNCH_APP -> {
            SectionLabel("App")
            Spacer(modifier = Modifier.height(6.dp))
            AppPickerRow(
                label = if (param1.isNotEmpty()) targetName else "Tap to select an app",
                selected = param1.isNotEmpty(),
                onClick = onPickApp
            )
        }

        ActionType.OPEN_URL -> {
            SectionLabel("URL")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = param1,
                onValueChange = onParam1Change,
                placeholder = { Text("https://example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ActionType.SEND_SMS -> {
            SectionLabel("Phone Number")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = param1,
                onValueChange = onParam1Change,
                placeholder = { Text("+1234567890") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Message")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = param2,
                onValueChange = onParam2Change,
                placeholder = { Text("Hello!") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ActionType.MAKE_CALL -> {
            SectionLabel("Phone Number")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = param1,
                onValueChange = onParam1Change,
                placeholder = { Text("+1234567890") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ActionType.SET_TIMER -> {
            SectionLabel("Duration (minutes)")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = param1,
                onValueChange = onParam1Change,
                placeholder = { Text("5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ActionType.SET_ALARM -> {
            SectionLabel("Time")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = param1,
                onValueChange = onParam1Change,
                placeholder = { Text("07:30") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ActionType.TOGGLE_WIFI, ActionType.TOGGLE_BLUETOOTH -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = when (ActionType.valueOf(actionType)) {
                        ActionType.TOGGLE_WIFI      -> "Opens the Wi-Fi settings panel."
                        ActionType.TOGGLE_BLUETOOTH -> "Opens the Bluetooth settings panel."
                        else                        -> ""
                    },
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS,
        ActionType.MEDIA_STOP, ActionType.MEDIA_START -> {
            // Media control presets — no additional target needed
        }

        ActionType.NAVIGATE_TO -> {
            // Directions preset — destination comes from voice input, nothing to configure here
        }
    }
}

private fun formatActionType(type: String): String = when (type) {
    "LAUNCH_APP"       -> "Launch App"
    "OPEN_URL"         -> "Open URL"
    "SEND_SMS"         -> "Send SMS"
    "MAKE_CALL"        -> "Make Phone Call"
    "SET_TIMER"        -> "Set Timer"
    "SET_ALARM"        -> "Set Alarm"
    "TOGGLE_WIFI"      -> "Wi-Fi"
    "TOGGLE_BLUETOOTH" -> "Bluetooth"
    "MEDIA_NEXT"       -> "Next Track"
    "MEDIA_PREVIOUS"   -> "Previous Track"
    "MEDIA_STOP"       -> "Stop Music"
    "MEDIA_START"      -> "Media Control"
    "NAVIGATE_TO"      -> "Navigation"
    else               -> type.replace("_", " ")
}
