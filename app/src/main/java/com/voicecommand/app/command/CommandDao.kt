package com.voicecommand.app.command

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {

    @Query("SELECT * FROM command_phrases")
    fun getAllPhrases(): Flow<List<CommandPhraseEntity>>

    @Query("SELECT * FROM command_phrases WHERE phrase = :phrase LIMIT 1")
    suspend fun getByExactPhrase(phrase: String): CommandPhraseEntity?

    @Query("SELECT DISTINCT commandId, actionType, actionTarget, displayLabel FROM command_phrases ORDER BY commandId")
    fun getAllCommandGroups(): Flow<List<CommandGroupSummary>>

    @Query("SELECT * FROM command_phrases WHERE commandId = :commandId")
    suspend fun getPhrasesByCommandId(commandId: String): List<CommandPhraseEntity>

    @Insert
    suspend fun insertAll(phrases: List<CommandPhraseEntity>)

    @Insert
    suspend fun insertOne(entity: CommandPhraseEntity): Long

    @Query("DELETE FROM command_phrases WHERE commandId = :commandId")
    suspend fun deleteByCommandId(commandId: String)

    @Query("SELECT COUNT(*) FROM command_phrases")
    suspend fun count(): Int
}

data class CommandGroupSummary(
    val commandId: String,
    val actionType: String,
    val actionTarget: String,
    val displayLabel: String
)
