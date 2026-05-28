package com.voicecommand.app.command

import com.voicecommand.app.VoiceCommandApp

object CommandMatcher {
    suspend fun match(input: String): Command? {
        return VoiceCommandApp.instance.commandRepository.match(input)
    }
}
