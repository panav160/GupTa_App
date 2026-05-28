package com.voicecommand.app.command

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.net.Uri
import android.app.SearchManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.voicecommand.app.VoiceCommandApp

class ActionExecutor(private val context: Context) {

    sealed class ExecutionResult {
        data class Success(val message: String) : ExecutionResult()
        data class Failed(val message: String) : ExecutionResult()
    }

    fun execute(command: Command): ExecutionResult {
        return try {
            when (command.id) {
                "timer_preset"          -> executeTimerPreset(command)
                "play_song_preset"      -> executePlaySongPreset(command)
                "directions_preset"     -> executeDirectionsPreset(command)
                "next_track_preset",
                "previous_track_preset",
                "back_track_preset",
                "stop_track_preset",
                "start_track_preset"    -> executeMediaControl(command)
                else -> {
                    val targetIntent = buildTargetIntent(command) ?: return ExecutionResult.Failed("Unknown command: ${command.id}")
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    // Capture before we switch screens
                    val previousPkg = CommandAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
                    when (command.actionType) {
                        ActionType.TOGGLE_WIFI,
                        ActionType.TOGGLE_BLUETOOTH -> scheduleReturnToPreviousApp(previousPkg, 2000L)
                        ActionType.SET_ALARM        -> scheduleReturnToPreviousApp(previousPkg, 1500L)
                        else -> { /* user needs to stay in the opened screen */ }
                    }
                    context.startActivity(targetIntent)
                    ExecutionResult.Success(getSuccessMessage(command))
                }
            }
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Failed("Action failed for ${command.id}")
        } catch (e: SecurityException) {
            ExecutionResult.Failed("Permission denied for ${command.id}")
        }
    }

    fun executeViaAlarm(command: Command): ExecutionResult {
        return try {
            when (command.id) {
                "timer_preset"          -> executeTimerPreset(command)
                "play_song_preset"      -> executePlaySongPreset(command)
                "directions_preset"     -> executeDirectionsPreset(command)
                "next_track_preset",
                "previous_track_preset",
                "back_track_preset",
                "stop_track_preset",
                "start_track_preset"    -> executeMediaControl(command)
                else -> {
                    val targetIntent = buildTargetIntent(command) ?: return ExecutionResult.Failed("Unknown command: ${command.id}")
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    val bridgeIntent = Intent(context, BridgeActivity::class.java).apply {
                        putExtra(BridgeActivity.EXTRA_TARGET_INTENT, targetIntent)
                    }
                    val requestCode = System.currentTimeMillis().toInt()
                    val pendingIntent = PendingIntent.getActivity(
                        context, requestCode, bridgeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val triggerTime = System.currentTimeMillis() + 300L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !alarmManager.canScheduleExactAlarms()
                    ) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                    ExecutionResult.Success(getSuccessMessage(command))
                }
            }
        } catch (e: Exception) {
            ExecutionResult.Failed("Alarm execution failed for ${command.id}")
        }
    }

    private fun executeTimerPreset(command: Command): ExecutionResult {
        val minutes = command.actionParams["duration"]?.toIntOrNull()
            ?: return ExecutionResult.Failed("No duration specified")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return scheduleTimerNotification(minutes)
        }
        val previousPkg = CommandAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
        return try {
            val a11y = CommandAccessibilityService.instance
            if (a11y != null) {
                a11y.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            scheduleReturnToPreviousApp(previousPkg, 1500L)
            ExecutionResult.Success("Setting timer for ${minutes}min")
        } catch (e: SecurityException) {
            scheduleTimerNotification(minutes)
        }
    }

    /**
     * Opens Google Maps in turn-by-turn navigation mode for the spoken place name.
     * Falls back to a browser Maps URL if the native app is not installed.
     */
    private fun executeDirectionsPreset(command: Command): ExecutionResult {
        val place = command.actionParams["place"]
            ?: return ExecutionResult.Failed("No destination specified")

        // Native navigation intent — opens directly into turn-by-turn mode
        val navUri = Uri.parse("google.navigation:q=${Uri.encode(place)}")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }

        return try {
            val a11y = CommandAccessibilityService.instance
            if (navIntent.resolveActivity(context.packageManager) != null) {
                if (a11y != null) a11y.startActivity(navIntent) else context.startActivity(navIntent)
            } else {
                // Fallback: open in any browser / maps-capable app
                val webUri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(place)}&travelmode=driving"
                )
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                }
                if (a11y != null) a11y.startActivity(webIntent) else context.startActivity(webIntent)
            }
            ExecutionResult.Success("Navigating to $place")
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Failed("Maps app not found")
        }
    }

    /**
     * Plays a song via the chosen music app.
     *
     * Two-phase strategy:
     * 1. Background path (preferred): Discover the music app's MediaBrowserService and call
     *    playFromSearch() directly — the screen never leaves the current app. Works for any
     *    music player that doesn't whitelist MediaBrowser callers.
     * 2. UI fallback: Fire INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH with FLAG_ACTIVITY_NO_ANIMATION
     *    (minimises the visual flash), then use the Accessibility Service to tap the first
     *    matching song result and press Back once audio starts. Required for Spotify and
     *    YouTube Music which block third-party MediaBrowser connections.
     */
    private fun executePlaySongPreset(command: Command): ExecutionResult {
        val songName = command.actionParams["song"]
            ?: return ExecutionResult.Failed("No song name specified")
        val musicPkg = command.actionParams["package"]

        // Capture the current foreground app NOW, before we switch to the music app.
        // We re-launch this package directly when playback starts instead of pressing
        // Back — that way we always land on exactly the screen the user was on,
        // regardless of how many screens the music app has on its own back stack.
        val previousPkg = CommandAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Phase 1 — try fully-background MediaBrowser playback (no UI switch)
        if (musicPkg != null) {
            val serviceComponent = findMediaBrowserService(musicPkg)
            if (serviceComponent != null) {
                tryMediaBrowserPlayback(songName, serviceComponent,
                    onFailed = { launchSearchAndClick(songName, musicPkg, audioManager, previousPkg) })
                return ExecutionResult.Success("Playing \"$songName\"")
            }
        }

        // Phase 2 — UI approach with no transition animation to minimise the flash
        launchSearchAndClick(songName, musicPkg, audioManager, previousPkg)
        return ExecutionResult.Success("Playing \"$songName\"")
    }

    /** Queries PackageManager for a MediaBrowserService inside [pkg]. */
    private fun findMediaBrowserService(pkg: String): ComponentName? {
        val intent = Intent("android.media.browse.MediaBrowserService").setPackage(pkg)
        val services = context.packageManager.queryIntentServices(intent, 0)
        return services.firstOrNull()?.serviceInfo?.let { ComponentName(it.packageName, it.name) }
    }

    /**
     * Connects to [serviceComponent] via MediaBrowser and fires playFromSearch.
     * If the app rejects our connection (e.g. Spotify, YT Music), [onFailed] is called
     * so the caller can fall back to the UI approach.
     */
    private fun tryMediaBrowserPlayback(
        songName: String,
        serviceComponent: ComponentName,
        onFailed: () -> Unit
    ) {
        // MediaBrowser must be created on the main thread
        Handler(Looper.getMainLooper()).post {
            val holder = arrayOfNulls<MediaBrowser>(1)
            val browser = MediaBrowser(context, serviceComponent,
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        try {
                            val controller = MediaController(context, holder[0]!!.sessionToken)
                            controller.transportControls.playFromSearch(songName, Bundle())
                            // Playback started in background — nothing else needed
                        } catch (e: Exception) {
                            holder[0]?.disconnect()
                            onFailed()
                        }
                    }
                    override fun onConnectionFailed() {
                        // App whitelists callers — fall back to UI approach
                        onFailed()
                    }
                    override fun onConnectionSuspended() {
                        holder[0]?.disconnect()
                    }
                }, null)
            holder[0] = browser
            browser.connect()
        }
    }

    /**
     * Opens the music app via INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH (with no transition
     * animation), waits for the search results to render, taps the first song that matches
     * [songName], then presses Back once audio is active.
     */
    private fun launchSearchAndClick(
        songName: String,
        musicPkg: String?,
        audioManager: AudioManager,
        previousPkg: String?
    ) {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
            putExtra(SearchManager.QUERY, songName)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, songName)
            // NO_ANIMATION skips the enter/exit transition — screen changes are invisible
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            musicPkg?.let { setPackage(it) }
        }

        if (musicPkg == null && intent.resolveActivity(context.packageManager) == null) return

        try {
            val a11y = CommandAccessibilityService.instance
            if (a11y != null) a11y.startActivity(intent) else context.startActivity(intent)

            Handler(Looper.getMainLooper()).postDelayed({
                tryClickResultThenGoBack(audioManager, songName, previousPkg, attempt = 0)
            }, 2500L)
        } catch (_: ActivityNotFoundException) { /* no music app installed */ }
    }

    /**
     * Taps the first search-result card whose text matches [songName], then waits for
     * audio to start before pressing Back. Retries up to 3× if the results aren't rendered.
     */
    private fun tryClickResultThenGoBack(
        audioManager: AudioManager,
        songName: String,
        previousPkg: String?,
        attempt: Int
    ) {
        val a11y = CommandAccessibilityService.instance ?: return

        if (!audioManager.isMusicActive()) {
            val clicked = a11y.clickFirstSearchResult(songName)
            if (!clicked && attempt < 3) {
                Handler(Looper.getMainLooper()).postDelayed({
                    tryClickResultThenGoBack(audioManager, songName, previousPkg, attempt + 1)
                }, 1000L)
                return
            }
        }

        // Poll until audio is active, then bring the original app back to the foreground.
        // We re-launch by package rather than pressing Back — this skips the music app's
        // internal back stack and lands the user on exactly the screen they left.
        val deadline = System.currentTimeMillis() + 8_000L
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                when {
                    audioManager.isMusicActive() -> returnToPreviousApp(previousPkg)
                    System.currentTimeMillis() < deadline ->
                        Handler(Looper.getMainLooper()).postDelayed(this, 500L)
                    // else: timeout — leave music app open so the user can see what happened
                }
            }
        }, 500L)
    }

    /**
     * Brings [previousPkg] back to the foreground by re-launching its main activity.
     * Because Android re-uses an existing task when FLAG_ACTIVITY_NEW_TASK is set and a
     * task for that package already exists, the user lands on exactly the screen they left —
     * not the app's home screen. This is more reliable than GLOBAL_ACTION_BACK because it
     * skips whatever back stack the intermediate app may have pushed.
     */
    private fun returnToPreviousApp(previousPkg: String?) {
        if (previousPkg == null) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(previousPkg) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(launchIntent) } catch (_: Exception) { }
    }

    private fun scheduleReturnToPreviousApp(previousPkg: String?, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ returnToPreviousApp(previousPkg) }, delayMs)
    }

    /**
     * Sends a media key event to skip to the next or previous track.
     * AudioManager.dispatchMediaKeyEvent reaches whatever music app currently holds
     * audio focus — no package targeting or permissions required.
     */
    private fun executeMediaControl(command: Command): ExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (command.id) {
            "next_track_preset" -> {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
                ExecutionResult.Success("Next track")
            }
            "previous_track_preset" -> {
                // Single press — restarts the current song from the beginning
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                ExecutionResult.Success("Replaying song")
            }
            "back_track_preset" -> {
                // Double press — first press restarts current song, second goes to previous
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                Handler(Looper.getMainLooper()).postDelayed({
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                }, 300L)
                ExecutionResult.Success("Previous track")
            }
            "stop_track_preset" -> {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
                ExecutionResult.Success("Music stopped")
            }
            "start_track_preset" -> {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                ExecutionResult.Success("Music started")
            }
            else -> ExecutionResult.Failed("Unknown media command")
        }
    }

    private fun buildTargetIntent(command: Command): Intent? {
        return when (command.id) {
            "messaging_preset" -> buildMessagingIntent(command)
            "calling_preset" -> buildCallingIntent(command)
            "payment_preset" -> buildPaymentIntent(command)
            "browser_search" -> buildWebSearchIntent(command)
            "launch_preset" -> {
                val pkg = command.actionParams["target"] ?: return null
                context.packageManager.getLaunchIntentForPackage(pkg)
            }
            else -> when (command.actionType) {
                ActionType.LAUNCH_APP -> buildLaunchAppIntent(command)
                ActionType.OPEN_URL -> buildOpenUrlIntent(command)
                ActionType.SEND_SMS -> buildSendSmsIntent(command)
                ActionType.MAKE_CALL -> buildMakeCallIntent(command)
                ActionType.SET_TIMER -> buildSetTimerIntent(command)
                ActionType.SET_ALARM -> buildSetAlarmIntent(command)
                ActionType.TOGGLE_WIFI -> buildWifiIntent()
                ActionType.TOGGLE_BLUETOOTH -> buildBluetoothIntent()
                ActionType.NAVIGATE_TO,
                ActionType.MEDIA_NEXT,
                ActionType.MEDIA_PREVIOUS,
                ActionType.MEDIA_STOP,
                ActionType.MEDIA_START -> null  // handled before reaching buildTargetIntent
                else -> null
            }
        }
    }

    private fun getSuccessMessage(command: Command): String {
        return when (command.id) {
            "messaging_preset" -> "Opening messaging for ${command.actionParams["text"]}"
            "calling_preset" -> "Dialing ${command.actionParams["text"]}"
            "payment_preset" -> "Opening payment for ${command.actionParams["text"]}"
            "browser_search" -> "Searching for ${command.actionParams["query"]}"
            "launch_preset"         -> "Opening ${command.actionParams["appName"] ?: command.actionParams["target"]}"
            "directions_preset"     -> "Navigating to ${command.actionParams["place"]}"
            "play_song_preset"      -> "Playing \"${command.actionParams["song"]}\""
            "next_track_preset"     -> "Next track"
            "previous_track_preset" -> "Replaying song"
            "back_track_preset"     -> "Previous track"
            "stop_track_preset"     -> "Music stopped"
            "start_track_preset"    -> "Music started"
            else -> when (command.actionType) {
                ActionType.LAUNCH_APP -> "Opening ${command.actionParams["target"]}"
                ActionType.OPEN_URL -> "Opening ${command.actionParams["url"]}"
                ActionType.SEND_SMS -> "Opening SMS to ${command.actionParams["number"]}"
                ActionType.MAKE_CALL -> "Dialing ${command.actionParams["number"]}"
                ActionType.SET_TIMER -> "Setting timer for ${command.actionParams["duration"]}min"
                ActionType.SET_ALARM -> "Setting alarm for ${command.actionParams["time"]}"
                ActionType.TOGGLE_WIFI -> "Opening Wi-Fi settings"
                ActionType.TOGGLE_BLUETOOTH -> "Opening Bluetooth settings"
                ActionType.NAVIGATE_TO -> "Navigating to ${command.actionParams["place"]}"
                else -> "Executing ${command.id}"
            }
        }
    }

    private fun buildLaunchAppIntent(command: Command): Intent? {
        val target = command.actionParams["target"] ?: return null
        val pkgIntent = context.packageManager.getLaunchIntentForPackage(target)
        if (pkgIntent != null) return pkgIntent
        return when (target) {
            "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            "browser" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "phone" -> Intent(Intent.ACTION_DIAL)
            "clock" -> buildClockIntent()
            else -> null
        }
    }

    private fun buildMessagingIntent(command: Command): Intent? {
        val text = command.actionParams["text"] ?: return null
        val (rawName, message) = ContactResolver.extractNameAndMessage(text)
        val contactNames = VoiceCommandApp.instance.contactNames
        val name = if (contactNames.isNotEmpty()) {
            ContactResolver.findClosestContactName(rawName, contactNames) ?: rawName
        } else rawName
        val number = ContactResolver.findNumber(context, name) ?: return null
        val cleanNumber = number.replace(Regex("[^\\d+]"), "")
        val pkg = command.actionParams["package"]
        if (pkg == "com.whatsapp" || (pkg == null && isDefaultSmsApp("com.whatsapp"))) {
            val waUri = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
            return Intent(Intent.ACTION_VIEW, waUri)
        }
        return Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$number")).apply {
            putExtra("sms_body", message)
            pkg?.let { setPackage(it) }
        }
    }

    private fun isDefaultSmsApp(pkg: String): Boolean {
        return try {
            val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            defaultSms == pkg
        } catch (_: Exception) { false }
    }

    private fun buildCallingIntent(command: Command): Intent? {
        val name = command.actionParams["text"] ?: return null
        val number = ContactResolver.findNumber(context, name) ?: return null
        return Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
    }

    private fun buildPaymentIntent(command: Command): Intent? {
        val rawName = command.actionParams["text"] ?: return null
        val amount = command.actionParams["amount"] ?: return null
        val contactNames = VoiceCommandApp.instance.contactNames
        val name = if (contactNames.isNotEmpty()) {
            ContactResolver.findClosestContactName(rawName, contactNames) ?: rawName
        } else rawName
        val uri = Uri.parse("upi://pay?pn=${Uri.encode(name)}&am=$amount&cu=INR")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            command.actionParams["package"]?.let { setPackage(it) }
        }
    }

    private fun buildTimerIntent(command: Command): Intent? {
        val minutes = command.actionParams["duration"]?.toIntOrNull() ?: return null
        return Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    private fun buildSetTimerIntent(command: Command): Intent? {
        val minutes = command.actionParams["duration"]?.toIntOrNull() ?: return null
        return Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    private fun scheduleTimerNotification(minutes: Int): ExecutionResult {
        ensureTimerChannel()
        showTimerSetNotification(minutes)
        val triggerTime = System.currentTimeMillis() + (minutes * 60_000L)
        scheduleAlarm(minutes, triggerTime)
        Handler(Looper.getMainLooper()).postDelayed({
            showTimerDoneNotification(minutes)
        }, minutes * 60_000L)
        return ExecutionResult.Success("Timer set for ${minutes}min")
    }

    private fun ensureTimerChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TimerReceiver.TIMER_CHANNEL_ID,
                "Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer notifications"
                enableVibration(true)
                setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showTimerSetNotification(minutes: Int) {
        val openIntent = Intent(context, com.voicecommand.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, minutes + 1000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, TimerReceiver.TIMER_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setContentTitle("Timer Set")
            .setContentText("$minutes minute timer started")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1000 + minutes, notification)
    }

    private fun showTimerDoneNotification(minutes: Int) {
        val openIntent = Intent(context, com.voicecommand.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, minutes, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, TimerReceiver.TIMER_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setContentTitle("Timer Done!")
            .setContentText("$minutes minute timer completed")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(minutes, notification)
    }

    private fun scheduleAlarm(minutes: Int, triggerTime: Long) {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            putExtra(TimerReceiver.EXTRA_MINUTES, minutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, minutes, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun buildWebSearchIntent(command: Command): Intent? {
        val query = command.actionParams["query"] ?: return null
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            command.actionParams["package"]?.let { setPackage(it) }
        }
    }

    private fun buildOpenUrlIntent(command: Command): Intent? {
        val url = command.actionParams["url"] ?: return null
        val uri = if (url.startsWith("http://") || url.startsWith("https://")) Uri.parse(url)
                  else Uri.parse("https://$url")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            command.actionParams["package"]?.let { setPackage(it) }
        }
    }

    private fun buildSendSmsIntent(command: Command): Intent? {
        val number = command.actionParams["number"] ?: return null
        val message = command.actionParams["message"] ?: ""
        return Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$number")).apply {
            putExtra("sms_body", message)
        }
    }

    private fun buildMakeCallIntent(command: Command): Intent? {
        val number = command.actionParams["number"] ?: return null
        return Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
    }

    private fun buildSetAlarmIntent(command: Command): Intent? {
        val time = command.actionParams["time"] ?: return null
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    private fun buildWifiIntent(): Intent {
        val panel = Intent(Settings.Panel.ACTION_WIFI)
        if (panel.resolveActivity(context.packageManager) != null) {
            return panel
        }
        return Intent(Settings.ACTION_WIFI_SETTINGS)
    }

    private fun buildBluetoothIntent(): Intent {
        return Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    private fun buildClockIntent(): Intent {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        if (showAlarms.resolveActivity(context.packageManager) != null) {
            return showAlarms
        }
        val setAlarm = Intent(AlarmClock.ACTION_SET_ALARM)
        if (setAlarm.resolveActivity(context.packageManager) != null) {
            return setAlarm
        }
        val clockPkg = findClockPackage()
        if (clockPkg != null) {
            val pkgIntent = context.packageManager.getLaunchIntentForPackage(clockPkg)
            if (pkgIntent != null) return pkgIntent
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    private fun findClockPackage(): String? {
        val packages = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.android.alarmclock",
            "com.sec.android.app.clockpackage"
        )
        for (pkg in packages) {
            try {
                val pkgIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (pkgIntent != null) return pkg
            } catch (_: Exception) { }
        }
        return null
    }
}