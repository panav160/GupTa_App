package com.voicecommand.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import com.voicecommand.app.command.AppDatabase
import com.voicecommand.app.command.CommandRepository
import com.voicecommand.app.command.ContactResolver
import com.voicecommand.app.command.InstalledApp
import com.voicecommand.app.correction.CorrectionRepository

class VoiceCommandApp : Application() {
    lateinit var commandRepository: CommandRepository
        private set
    lateinit var correctionRepository: CorrectionRepository
        private set
    var contactNames: List<String> = emptyList()
        private set
    var installedApps: List<InstalledApp> = emptyList()
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.getInstance(this)
        commandRepository = CommandRepository(db.commandDao())
        correctionRepository = CorrectionRepository(db.correctionDao())
        loadContacts()
        loadInstalledApps()
    }

    fun loadContacts() {
        contactNames = ContactResolver.getAllContactNames(this)
    }

    fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        installedApps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
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
    }

    companion object {
        lateinit var instance: VoiceCommandApp
            private set
    }
}
