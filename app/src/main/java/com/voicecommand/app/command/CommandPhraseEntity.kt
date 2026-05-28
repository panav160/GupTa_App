package com.voicecommand.app.command

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_phrases")
data class CommandPhraseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
    val phrase: String,
    val actionType: String,
    val actionTarget: String,
    val displayLabel: String
)
