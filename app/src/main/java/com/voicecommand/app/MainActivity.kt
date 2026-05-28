package com.voicecommand.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voicecommand.app.audio.WakeWordService
import com.voicecommand.app.command.CommandAccessibilityService
import com.voicecommand.app.ui.screens.AppPickerDialog
import com.voicecommand.app.ui.screens.CommandEditScreen
import com.voicecommand.app.ui.screens.CommandsScreen
import com.voicecommand.app.ui.screens.ConnectScreen
import com.voicecommand.app.ui.screens.DeleteConfirmDialog
import com.voicecommand.app.ui.screens.MainScreen
import com.voicecommand.app.ui.theme.VoiceCommandTheme

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isInForeground = false
    }

    private var permissionsGranted by mutableStateOf(false)
    private var wakeWordBound = false
    private var showA11yDialog by mutableStateOf(false)
    private var showBatteryDialog by mutableStateOf(false)
    private var a11yMissing by mutableStateOf(false)
    private var batteryMissing by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = grants.values.all { it }
        if (permissionsGranted) {
            (application as VoiceCommandApp).loadContacts()
            checkA11yService()
            checkBatteryOptimization()
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        if (permissionsGranted) {
            checkA11yService()
            checkBatteryOptimization()
        }
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    private fun checkA11yService() {
        a11yMissing = CommandAccessibilityService.instance == null
        showA11yDialog = a11yMissing
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            batteryMissing = !pm.isIgnoringBatteryOptimizations(packageName)
            showBatteryDialog = batteryMissing
        }
    }

    private val wakeServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wakeWordBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            wakeWordBound = false
        }
    }

    private fun startWakeService(host: String, port: Int) {
        val intent = Intent(this, WakeWordService::class.java).apply {
            putExtra("SERVER_HOST", host)
            putExtra("SERVER_PORT", port)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWakeService() {
        try { unbindService(wakeServiceConnection) } catch (_: Exception) {}
        try { stopService(Intent(this, WakeWordService::class.java)) } catch (_: Exception) {}
        wakeWordBound = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            VoiceCommandTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceCommandNavHost(
                        permissionGranted = permissionsGranted,
                        showA11yDialog = showA11yDialog,
                        showBatteryDialog = showBatteryDialog,
                        a11yMissing = a11yMissing,
                        batteryMissing = batteryMissing,
                        onRequestPermission = { checkPermissions() },
                        onOpenA11ySettings = { openAccessibilitySettings() },
                        onDismissA11yDialog = { showA11yDialog = false },
                        onOpenBatterySettings = { openBatterySettings() },
                        onDismissBatteryDialog = { showBatteryDialog = false }
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        showA11yDialog = false
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openBatterySettings() {
        showBatteryDialog = false
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun checkPermissions() {
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            required.add(Manifest.permission.USE_EXACT_ALARM)
        }
        permissionsGranted = required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!permissionsGranted) {
            permissionLauncher.launch(required.toTypedArray())
        } else {
            checkA11yService()
            checkBatteryOptimization()
        }
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Voice : BottomNavItem("voice", "Voice", Icons.Default.Mic)
    data object Commands : BottomNavItem("commands", "Commands", Icons.Default.FormatListBulleted)
    data object Connect : BottomNavItem("connect", "Connect", Icons.Default.Laptop)
}

private val bottomNavItems = listOf(BottomNavItem.Voice, BottomNavItem.Commands, BottomNavItem.Connect)

@Composable
fun VoiceCommandNavHost(
    permissionGranted: Boolean,
    showA11yDialog: Boolean = false,
    showBatteryDialog: Boolean = false,
    a11yMissing: Boolean = false,
    batteryMissing: Boolean = false,
    onRequestPermission: () -> Unit,
    onOpenA11ySettings: () -> Unit = {},
    onDismissA11yDialog: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onDismissBatteryDialog: () -> Unit = {}
) {
    val navController = rememberNavController()
    val voiceViewModel: MainViewModel = viewModel()
    val commandsViewModel: CommandsViewModel = viewModel()

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            onRequestPermission()
        }
    }

    if (showA11yDialog) {
        AlertDialog(
            onDismissRequest = onDismissA11yDialog,
            title = { Text("Enable Accessibility Service") },
            text = {
                Text(
                    "To open apps from the background, Voice Command needs the Accessibility Service enabled.\n\n" +
                    "Go to Settings → Accessibility → Voice Command and turn it on."
                )
            },
            confirmButton = {
                Button(onClick = onOpenA11ySettings) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(onClick = onDismissA11yDialog) {
                    Text("Later")
                }
            }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = onDismissBatteryDialog,
            title = { Text("Disable Battery Optimization") },
            text = {
                Text(
                    "Your device may put Voice Command to sleep after a while, making it stop listening for \"Hi Gupta\".\n\n" +
                    "Please disable battery optimization for this app to keep it running reliably."
                )
            },
            confirmButton = {
                Button(onClick = onOpenBatterySettings) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(onClick = onDismissBatteryDialog) {
                    Text("Later")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Voice.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Voice.route) {
                MainScreen(
                    uiState = voiceViewModel.uiState,
                    statusText = voiceViewModel.statusText,
                    resultText = voiceViewModel.resultText,
                    commandText = voiceViewModel.commandText,
                    errorText = voiceViewModel.errorText,
                    pendingCommandLabel = voiceViewModel.pendingCommandLabel,
                    serverUrl = voiceViewModel.serverUrl,
                    onUpdateServerUrl = { url -> voiceViewModel.updateServerUrl(url) },
                    missingA11y = a11yMissing,
                    missingBattery = batteryMissing,
                    onFixA11y = onOpenA11ySettings,
                    onFixBattery = onOpenBatterySettings
                )
            }
            composable(BottomNavItem.Commands.route) {
                val commandGroups by commandsViewModel.commandGroups.collectAsState()
                CommandsScreen(
                    commandGroups = commandGroups,
                    selectedCommand = commandsViewModel.selectedCommand,
                    onSelectCommand = { commandsViewModel.selectCommand(it) },
                    onClearSelection = { commandsViewModel.clearSelection() },
                    onAddCommand = { commandsViewModel.showAddCommand() },
                    onEditCommand = { commandsViewModel.showEditCommand(it) },
                    onDeleteCommand = { commandsViewModel.requestDelete(it) }
                )

                if (commandsViewModel.showEditScreen) {
                    CommandEditScreen(
                        label = commandsViewModel.editLabel,
                        onLabelChange = { commandsViewModel.editLabel = it },
                        phrases = commandsViewModel.editPhrases,
                        onPhrasesChange = { commandsViewModel.editPhrases = it },
                        actionType = commandsViewModel.editActionType,
                        onActionTypeChange = { commandsViewModel.editActionType = it },
                        actionParam1 = commandsViewModel.editActionParam1,
                        onActionParam1Change = { commandsViewModel.editActionParam1 = it },
                        actionParam2 = commandsViewModel.editActionParam2,
                        onActionParam2Change = { commandsViewModel.editActionParam2 = it },
                        actionTargetName = commandsViewModel.editActionTargetName,
                        onPickApp = { commandsViewModel.showAppPicker = true },
                        onSave = { commandsViewModel.saveCommand() },
                        onCancel = { commandsViewModel.hideEditScreen() },
                        isEditing = commandsViewModel.isEditing,
                        saveEnabled = if (commandsViewModel.presetType != PresetType.NONE) {
                            commandsViewModel.editLabel.isNotBlank() &&
                            commandsViewModel.editTriggerWord.isNotBlank()
                        } else {
                            commandsViewModel.editLabel.isNotBlank() &&
                            commandsViewModel.editPhrases.lines().any { it.isNotBlank() } &&
                            (commandsViewModel.editActionType in listOf("TOGGLE_WIFI", "TOGGLE_BLUETOOTH") ||
                             commandsViewModel.editActionParam1.isNotBlank())
                        },
                        availableActionTypes = commandsViewModel.availableActionTypes,
                        showAppPicker = commandsViewModel.showAppPicker,
                        presetType = commandsViewModel.presetType,
                        triggerWord = commandsViewModel.editTriggerWord,
                        onTriggerWordChange = { commandsViewModel.editTriggerWord = it },
                        onPickAppCategory = { category -> commandsViewModel.showAppCategoryPicker = category },
                        editAppPackage = commandsViewModel.editAppPackage
                    )
                }

                if (commandsViewModel.showAppPicker) {
                    AppPickerDialog(
                        apps = commandsViewModel.installedApps,
                        onSelect = { app ->
                            commandsViewModel.editActionParam1 = app.packageName
                            commandsViewModel.editActionTargetName = app.appName
                            commandsViewModel.showAppPicker = false
                        },
                        onDismiss = { commandsViewModel.showAppPicker = false }
                    )
                }

                val pickerCategory = commandsViewModel.showAppCategoryPicker
                if (pickerCategory != null) {
                    val apps = when (pickerCategory) {
                        "browser"   -> commandsViewModel.browserApps
                        "messaging" -> commandsViewModel.messagingApps
                        "banking"   -> commandsViewModel.bankingApps
                        "music"     -> commandsViewModel.musicApps
                        else        -> emptyList()
                    }
                    if (apps.isNotEmpty()) {
                        AppPickerDialog(
                            apps = apps,
                            onSelect = { app ->
                                commandsViewModel.editAppPackage = app.packageName
                                commandsViewModel.editActionTargetName = app.appName
                                commandsViewModel.showAppCategoryPicker = null
                            },
                            onDismiss = { commandsViewModel.showAppCategoryPicker = null }
                        )
                    } else {
                        AppPickerDialog(
                            apps = emptyList(),
                            onSelect = {},
                            onDismiss = { commandsViewModel.showAppCategoryPicker = null }
                        )
                    }
                }

                DeleteConfirmDialog(
                    command = commandsViewModel.deleteTarget,
                    onConfirm = { commandsViewModel.confirmDelete() },
                    onDismiss = { commandsViewModel.cancelDelete() }
                )
            }
            composable(BottomNavItem.Connect.route) {
                ConnectScreen(
                    isConnectedToServer = voiceViewModel.isConnectedToServer,
                    serverUrl = voiceViewModel.serverUrl
                )
            }
        }
    }
}
