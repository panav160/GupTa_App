package com.voicecommand.app.command

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.voicecommand.app.correction.CorrectionDao
import com.voicecommand.app.correction.CorrectionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [CommandPhraseEntity::class, CorrectionEntity::class], version = 15, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun commandDao(): CommandDao
    abstract fun correctionDao(): CorrectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = buildDatabase(context)
                INSTANCE = db
                CoroutineScope(Dispatchers.IO).launch {
                    prepopulate(db.commandDao())
                }
                db
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "voicecommand.db"
            ).fallbackToDestructiveMigration().build()
        }

        private suspend fun prepopulate(dao: CommandDao) {
            dao.deleteByCommandId("whatsapp_preset")

            // Migrate: seed core presets that are only in the fresh-install block on older versions
            if (dao.getPhrasesByCommandId("browser_search").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "browser_search", phrase = "search", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Browser Search")
                ))
            }
            if (dao.getPhrasesByCommandId("messaging_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "messaging_preset", phrase = "message", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Send Message"),
                    CommandPhraseEntity(commandId = "messaging_preset", phrase = "text", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Send Message")
                ))
            }
            if (dao.getPhrasesByCommandId("calling_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "calling_preset", phrase = "call", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Call Contact")
                ))
            }
            if (dao.getPhrasesByCommandId("payment_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "payment_preset", phrase = "pay", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Make Payment")
                ))
            }
            if (dao.getPhrasesByCommandId("timer_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "timer_preset", phrase = "set timer", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Set Timer")
                ))
            }

            // Migrate: seed launch_preset if missing (added after initial release)
            if (dao.getPhrasesByCommandId("launch_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "launch_preset", phrase = "launch", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Launch App")
                ))
            }

            // Migrate: seed play_song_preset if missing
            if (dao.getPhrasesByCommandId("play_song_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "play_song_preset", phrase = "play", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Play Song")
                ))
            }

            // Migrate: seed next_track_preset if missing
            if (dao.getPhrasesByCommandId("next_track_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "next_track_preset", phrase = "next track", actionType = "MEDIA_NEXT", actionTarget = "", displayLabel = "Next Track")
                ))
            }

            // Migrate: seed previous_track_preset if missing, or rename its phrase to "replay"
            val prevTrackPhrases = dao.getPhrasesByCommandId("previous_track_preset")
            if (prevTrackPhrases.isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "previous_track_preset", phrase = "replay", actionType = "MEDIA_PREVIOUS", actionTarget = "", displayLabel = "Replay Song")
                ))
            } else if (prevTrackPhrases.any { it.phrase == "previous track" }) {
                // Rename old default so "previous track" is freed up for the new double-press preset
                dao.deleteByCommandId("previous_track_preset")
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "previous_track_preset", phrase = "replay", actionType = "MEDIA_PREVIOUS", actionTarget = "", displayLabel = "Replay Song")
                ))
            }

            // Migrate: seed back_track_preset (double-press = previous song) if missing
            if (dao.getPhrasesByCommandId("back_track_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "back_track_preset", phrase = "previous track", actionType = "MEDIA_PREVIOUS", actionTarget = "", displayLabel = "Previous Track")
                ))
            }

            // Migrate: update directions_preset trigger from "directions" to "navigate to"
            val directionsPhrases = dao.getPhrasesByCommandId("directions_preset")
            if (directionsPhrases.isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "directions_preset", phrase = "navigate to", actionType = "NAVIGATE_TO", actionTarget = "", displayLabel = "Get Directions")
                ))
            } else if (directionsPhrases.any { it.phrase == "directions" }) {
                dao.deleteByCommandId("directions_preset")
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "directions_preset", phrase = "navigate to", actionType = "NAVIGATE_TO", actionTarget = "", displayLabel = "Get Directions")
                ))
            }

            // Migrate: seed stop_track_preset if missing
            if (dao.getPhrasesByCommandId("stop_track_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "stop_track_preset", phrase = "stop", actionType = "MEDIA_STOP", actionTarget = "", displayLabel = "Stop Music")
                ))
            }

            // Migrate: seed start_track_preset if missing
            if (dao.getPhrasesByCommandId("start_track_preset").isEmpty()) {
                dao.insertAll(listOf(
                    CommandPhraseEntity(commandId = "start_track_preset", phrase = "start", actionType = "MEDIA_START", actionTarget = "", displayLabel = "Start Music")
                ))
            }

            // Remove pause/play presets if they were seeded in a previous version
            dao.deleteByCommandId("pause_track_preset")
            dao.deleteByCommandId("play_track_preset")

            if (dao.count() > 0) return
            dao.insertAll(
                listOf(
                    CommandPhraseEntity(commandId = "browser_search", phrase = "search", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Browser Search"),

                    CommandPhraseEntity(commandId = "messaging_preset", phrase = "message", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Send Message"),
                    CommandPhraseEntity(commandId = "messaging_preset", phrase = "text", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Send Message"),

                    CommandPhraseEntity(commandId = "calling_preset", phrase = "call", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Call Contact"),

                    CommandPhraseEntity(commandId = "payment_preset", phrase = "pay", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Make Payment"),

                    CommandPhraseEntity(commandId = "timer_preset", phrase = "set timer", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Set Timer"),

                    CommandPhraseEntity(commandId = "launch_preset", phrase = "launch", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Launch App"),

                    CommandPhraseEntity(commandId = "play_song_preset", phrase = "play", actionType = "LAUNCH_APP", actionTarget = "", displayLabel = "Play Song"),

                    CommandPhraseEntity(commandId = "next_track_preset", phrase = "next track", actionType = "MEDIA_NEXT", actionTarget = "", displayLabel = "Next Track"),

                    CommandPhraseEntity(commandId = "previous_track_preset", phrase = "replay", actionType = "MEDIA_PREVIOUS", actionTarget = "", displayLabel = "Replay Song"),

                    CommandPhraseEntity(commandId = "back_track_preset", phrase = "previous track", actionType = "MEDIA_PREVIOUS", actionTarget = "", displayLabel = "Previous Track"),

                    CommandPhraseEntity(commandId = "directions_preset", phrase = "navigate to", actionType = "NAVIGATE_TO", actionTarget = "", displayLabel = "Get Directions"),

                    CommandPhraseEntity(commandId = "stop_track_preset", phrase = "stop", actionType = "MEDIA_STOP", actionTarget = "", displayLabel = "Stop Music"),

                    CommandPhraseEntity(commandId = "start_track_preset", phrase = "start", actionType = "MEDIA_START", actionTarget = "", displayLabel = "Start Music"),

                )
            )
        }
    }
}
