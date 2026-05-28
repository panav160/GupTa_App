package com.voicecommand.app.command

import com.voicecommand.app.VoiceCommandApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CommandRepository(private val dao: CommandDao) {

    val allCommandGroups: Flow<List<CommandGroupSummary>> = dao.getAllCommandGroups()

    val allPhrases: Flow<List<CommandPhraseEntity>> = dao.getAllPhrases()

    private val triggerWords = mapOf<String, String>()

    private val presetIds = setOf("browser_search", "messaging_preset", "calling_preset", "payment_preset", "timer_preset", "launch_preset", "play_song_preset", "next_track_preset", "previous_track_preset", "back_track_preset", "directions_preset", "stop_track_preset", "start_track_preset")

    suspend fun match(input: String): Command? {
        val phrases = allPhrases.first()
        if (phrases.isEmpty()) return null

        val normalized = input.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null

        val groups = phrases.groupBy { it.commandId }
        val commandList = groups.map { (commandId, entities) ->
            val first = entities.first()
            Command(
                id = commandId,
                phrases = entities.map { it.phrase },
                actionType = ActionType.valueOf(first.actionType),
                actionParams = parseActionParams(first.actionType, first.actionTarget)
            )
        }

        val exact = commandList.firstOrNull { cmd ->
            cmd.id !in presetIds && cmd.phrases.any { it == normalized }
        }
        if (exact != null) return exact

        val searchResult = matchSearchPattern(normalized, phrases)
        if (searchResult != null) return searchResult

        val messagingResult = matchMessagingPattern(normalized, phrases)
        if (messagingResult != null) return messagingResult

        val callingResult = matchCallingPattern(normalized, phrases)
        if (callingResult != null) return callingResult

        val paymentResult = matchPaymentPattern(normalized, phrases)
        if (paymentResult != null) return paymentResult

        val timerResult = matchTimerPattern(normalized, phrases)
        if (timerResult != null) return timerResult

        val launchResult = matchLaunchPattern(normalized, phrases)
        if (launchResult != null) return launchResult

        val playSongResult = matchPlaySongPattern(normalized, phrases)
        if (playSongResult != null) return playSongResult

        val mediaControlResult = matchMediaControlPattern(normalized, phrases)
        if (mediaControlResult != null) return mediaControlResult

        val directionsResult = matchDirectionsPattern(normalized, phrases)
        if (directionsResult != null) return directionsResult

        val words = normalized.split(" ").toSet()
        val hasOpen = "open" in words || "launch" in words || "start" in words || "show" in words || "go" in words

        for (word in words) {
            val commandId = triggerWords[word]
            if (commandId != null && commandId !in presetIds) {
                return commandList.firstOrNull { it.id == commandId }
            }
        }

        if (hasOpen) {
            for ((trigger, commandId) in triggerWords) {
                if (normalized.contains(trigger) && commandId !in presetIds) {
                    return commandList.firstOrNull { it.id == commandId }
                }
            }
        }

        val partial = commandList.firstOrNull { cmd ->
            cmd.id !in presetIds && cmd.phrases.any { phrase ->
                normalized.contains(phrase) || phrase.contains(normalized)
            }
        }
        if (partial != null) return partial

        return null
    }

    private fun matchMessagingPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entities = phrases.filter { it.commandId == "messaging_preset" }
        for (entity in entities) {
            val triggerWord = entity.phrase.lowercase()
            val pattern = Regex("""^${Regex.escape(triggerWord)}\s+(.+)$""", RegexOption.IGNORE_CASE)
            val match = pattern.find(normalized)
            if (match != null) {
                val text = match.groupValues[1].trim()
                if (text.isNotBlank()) {
                    val msgPkg = entity.actionTarget.ifBlank { null }
                    val params = mutableMapOf("text" to text)
                    if (msgPkg != null) params["package"] = msgPkg
                    return Command(
                        id = "messaging_preset",
                        phrases = listOf(triggerWord),
                        actionType = ActionType.SEND_SMS,
                        actionParams = params
                    )
                }
            }
        }
        return null
    }

    private fun matchCallingPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entities = phrases.filter { it.commandId == "calling_preset" }
        for (entity in entities) {
            val triggerWord = entity.phrase.lowercase()
            val escaped = Regex.escape(triggerWord)

            val patterns = listOf(
                Regex("""^${escaped}e?d?\s+(.+)$""", RegexOption.IGNORE_CASE),
                Regex("""\b${escaped}e?d?\s+(.+)$""", RegexOption.IGNORE_CASE)
            )
            for (pattern in patterns) {
                val match = pattern.find(normalized)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    if (name.isNotBlank()) {
                        val contactNames = VoiceCommandApp.instance.contactNames
                        val resolvedName = if (contactNames.isNotEmpty()) {
                            ContactResolver.findClosestContactName(name, contactNames) ?: name
                        } else name
                        return Command(
                            id = "calling_preset",
                            phrases = listOf(triggerWord),
                            actionType = ActionType.MAKE_CALL,
                            actionParams = mapOf("text" to resolvedName)
                        )
                    }
                }
            }

            if (normalized.contains("call") || normalized.contains("coll") ||
                normalized.contains("kall") || normalized.contains("cull") ||
                normalized.contains("caul")) {
                val name = normalized.replace(Regex("""\b\w*cal?\w*d?\w*\s+""", RegexOption.IGNORE_CASE), "").trim()
                if (name.isNotBlank()) {
                    val contactNames = VoiceCommandApp.instance.contactNames
                    val resolvedName = if (contactNames.isNotEmpty()) {
                        ContactResolver.findClosestContactName(name, contactNames) ?: name
                    } else name
                    return Command(
                        id = "calling_preset",
                        phrases = listOf(triggerWord),
                        actionType = ActionType.MAKE_CALL,
                        actionParams = mapOf("text" to resolvedName)
                    )
                }
            }
        }
        return null
    }

    private fun matchPaymentPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entities = phrases.filter { it.commandId == "payment_preset" }
        for (entity in entities) {
            val triggerWord = entity.phrase.lowercase()
            val escaped = Regex.escape(triggerWord)
            val digitPattern = Regex("""^${escaped}\s+(.+?)\s+(\d+)\s*(?:rupees|rs\.?)?$""", RegexOption.IGNORE_CASE)
            val digitMatch = digitPattern.find(normalized)
            if (digitMatch != null) {
                val name = digitMatch.groupValues[1].trim()
                val amount = digitMatch.groupValues[2].trim()
                if (name.isNotBlank() && amount.isNotBlank()) {
                    return paymentCommand(entity, name, amount)
                }
            }
            val wordPattern = Regex("""^${escaped}\s+(.+?)\s+(hundred|thousand|fifty|twenty|thirty|forty|sixty|seventy|eighty|ninety|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen)\s*$""", RegexOption.IGNORE_CASE)
            val wordMatch = wordPattern.find(normalized)
            if (wordMatch != null) {
                val name = wordMatch.groupValues[1].trim()
                val amountWord = wordMatch.groupValues[2].trim().lowercase()
                val amount = wordToNumber(amountWord)
                if (name.isNotBlank() && amount != null) {
                    return paymentCommand(entity, name, amount)
                }
            }
        }
        return null
    }

    private fun paymentCommand(entity: CommandPhraseEntity, name: String, amount: String): Command {
        val bankPkg = entity.actionTarget.ifBlank { null }
        val params = mutableMapOf("text" to name, "amount" to amount)
        if (bankPkg != null) params["package"] = bankPkg
        return Command(
            id = "payment_preset",
            phrases = listOf(entity.phrase.lowercase()),
            actionType = ActionType.OPEN_URL,
            actionParams = params
        )
    }

    private fun wordToNumber(word: String): String? {
        return when (word) {
            "one" -> "1"; "two" -> "2"; "three" -> "3"; "four" -> "4"; "five" -> "5"
            "six" -> "6"; "seven" -> "7"; "eight" -> "8"; "nine" -> "9"; "ten" -> "10"
            "eleven" -> "11"; "twelve" -> "12"; "thirteen" -> "13"; "fourteen" -> "14"
            "fifteen" -> "15"; "sixteen" -> "16"; "seventeen" -> "17"; "eighteen" -> "18"
            "nineteen" -> "19"; "twenty" -> "20"; "thirty" -> "30"; "forty" -> "40"
            "fifty" -> "50"; "sixty" -> "60"; "seventy" -> "70"; "eighty" -> "80"
            "ninety" -> "90"; "hundred" -> "100"; "thousand" -> "1000"
            else -> null
        }
    }

    private fun matchTimerPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entities = phrases.filter { it.commandId == "timer_preset" }
        for (entity in entities) {
            val triggerWord = entity.phrase.lowercase()
            val escaped = Regex.escape(triggerWord)
            val pattern = Regex("""^${escaped}\s+(?:of\s+)?(\d+)(?:\s*minutes?|\s*min)?$""", RegexOption.IGNORE_CASE)
            val match = pattern.find(normalized)
            if (match != null) {
                val minutes = match.groupValues[1].trim()
                if (minutes.isNotBlank()) {
                    return Command(
                        id = "timer_preset",
                        phrases = listOf(triggerWord),
                        actionType = ActionType.SET_TIMER,
                        actionParams = mapOf("duration" to minutes)
                    )
                }
            }
        }
        return null
    }

    private fun matchLaunchPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entities = phrases.filter { it.commandId == "launch_preset" }
        for (entity in entities) {
            val triggerWord = entity.phrase.lowercase()
            val escaped = Regex.escape(triggerWord)
            val pattern = Regex("""^${escaped}\s+(.+)$""", RegexOption.IGNORE_CASE)
            val match = pattern.find(normalized)
            if (match != null) {
                val spokenAppName = match.groupValues[1].trim()
                if (spokenAppName.isNotBlank()) {
                    val result = AppResolver.findBestMatch(spokenAppName) ?: continue
                    return Command(
                        id = "launch_preset",
                        phrases = listOf(triggerWord),
                        actionType = ActionType.LAUNCH_APP,
                        actionParams = mapOf("target" to result.first, "appName" to result.second)
                    )
                }
            }
        }
        return null
    }

    private fun matchPlaySongPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entities = phrases.filter { it.commandId == "play_song_preset" }
        for (entity in entities) {
            val triggerWord = entity.phrase.lowercase()
            val escaped = Regex.escape(triggerWord)
            // Matches: "play <song>", "play song <song>", "play the song <song>", "play a song <song>"
            val pattern = Regex(
                """^${escaped}\s+(?:(?:a\s+)?(?:the\s+)?song\s+)?(.+)$""",
                RegexOption.IGNORE_CASE
            )
            val match = pattern.find(normalized)
            if (match != null) {
                val songName = match.groupValues[1].trim()
                if (songName.isNotBlank()) {
                    val musicPkg = entity.actionTarget.ifBlank { null }
                    val params = mutableMapOf("song" to songName)
                    if (musicPkg != null) params["package"] = musicPkg
                    return Command(
                        id = "play_song_preset",
                        phrases = listOf(triggerWord),
                        actionType = ActionType.LAUNCH_APP,
                        actionParams = params
                    )
                }
            }
        }
        return null
    }

    /**
     * Matches "next track / next song / skip" → next_track_preset
     * and "previous track / previous song / last track" → previous_track_preset.
     * The trigger phrase is whatever the user stored; we also accept common synonyms
     * so recognition is robust even if the STT returns a slightly different word.
     */
    private fun matchMediaControlPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val nextPhrases = phrases.filter { it.commandId == "next_track_preset" }
        for (entity in nextPhrases) {
            val trigger = entity.phrase.lowercase()
            if (normalized == trigger || normalized.contains(trigger)) {
                return Command(id = "next_track_preset", phrases = listOf(trigger),
                    actionType = ActionType.MEDIA_NEXT, actionParams = emptyMap())
            }
        }
        val prevPhrases = phrases.filter { it.commandId == "previous_track_preset" }
        for (entity in prevPhrases) {
            val trigger = entity.phrase.lowercase()
            if (normalized == trigger || normalized.contains(trigger)) {
                return Command(id = "previous_track_preset", phrases = listOf(trigger),
                    actionType = ActionType.MEDIA_PREVIOUS, actionParams = emptyMap())
            }
        }
        val backPhrases = phrases.filter { it.commandId == "back_track_preset" }
        for (entity in backPhrases) {
            val trigger = entity.phrase.lowercase()
            if (normalized == trigger || normalized.contains(trigger)) {
                return Command(id = "back_track_preset", phrases = listOf(trigger),
                    actionType = ActionType.MEDIA_PREVIOUS, actionParams = emptyMap())
            }
        }
        // Use exact match only for stop/start to avoid conflicts with other commands
        val stopPhrases = phrases.filter { it.commandId == "stop_track_preset" }
        for (entity in stopPhrases) {
            val trigger = entity.phrase.lowercase()
            if (normalized == trigger) {
                return Command(id = "stop_track_preset", phrases = listOf(trigger),
                    actionType = ActionType.MEDIA_STOP, actionParams = emptyMap())
            }
        }
        val startPhrases = phrases.filter { it.commandId == "start_track_preset" }
        for (entity in startPhrases) {
            val trigger = entity.phrase.lowercase()
            if (normalized == trigger) {
                return Command(id = "start_track_preset", phrases = listOf(trigger),
                    actionType = ActionType.MEDIA_START, actionParams = emptyMap())
            }
        }
        return null
    }

    /**
     * Matches "directions to X", "direction to X" (using the stored trigger word), plus
     * common synonyms like "navigate to X", "take me to X", "route to X".
     */
    private fun matchDirectionsPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val entity = phrases.firstOrNull { it.commandId == "directions_preset" } ?: return null
        val triggerWord = entity.phrase.lowercase()
        val escaped = Regex.escape(triggerWord)

        // e.g. "directions to the park" or "direction to airport"
        val triggerPattern = Regex("""^${escaped}s?\s+to\s+(.+)$""", RegexOption.IGNORE_CASE)
        triggerPattern.find(normalized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { place ->
            return directionsCommand(triggerWord, place)
        }

        // Synonyms that work regardless of the stored trigger word
        val synonymPattern = Regex(
            """^(?:navigate|navigation|route|routing|take\s+me|go)\s+to\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        synonymPattern.find(normalized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { place ->
            return directionsCommand(triggerWord, place)
        }

        return null
    }

    private fun directionsCommand(triggerWord: String, place: String) = Command(
        id = "directions_preset",
        phrases = listOf(triggerWord),
        actionType = ActionType.NAVIGATE_TO,
        actionParams = mapOf("place" to place)
    )

    private fun matchSearchPattern(normalized: String, phrases: List<CommandPhraseEntity>): Command? {
        val searchEntity = phrases.firstOrNull { it.commandId == "browser_search" } ?: return null
        val triggerWord = searchEntity.phrase.lowercase()
        val escaped = Regex.escape(triggerWord)
        val pattern = Regex("""^${escaped}e?d?\s+(?:for\s+)?(.+)$""", RegexOption.IGNORE_CASE)
        val match = pattern.find(normalized)
        if (match != null) {
            val query = match.groupValues[1].trim()
            if (query.isNotBlank()) {
                val browserPkg = searchEntity.actionTarget.ifBlank { null }
                val params = mutableMapOf("query" to query)
                if (browserPkg != null) {
                    params["package"] = browserPkg
                }
                return Command(
                    id = "browser_search",
                    phrases = listOf(triggerWord),
                    actionType = ActionType.OPEN_URL,
                    actionParams = params
                )
            }
        }
        return null
    }

    suspend fun addCommand(
        commandId: String,
        displayLabel: String,
        phrases: List<String>,
        actionType: String,
        actionTarget: String
    ) {
        dao.insertAll(phrases.map { phrase ->
            CommandPhraseEntity(
                commandId = commandId,
                phrase = phrase.trim().lowercase(),
                actionType = actionType,
                actionTarget = actionTarget,
                displayLabel = displayLabel
            )
        })
    }

    suspend fun updateCommand(
        commandId: String,
        displayLabel: String,
        phrases: List<String>,
        actionType: String,
        actionTarget: String
    ) {
        dao.deleteByCommandId(commandId)
        addCommand(commandId, displayLabel, phrases, actionType, actionTarget)
    }

    suspend fun deleteCommand(commandId: String) {
        dao.deleteByCommandId(commandId)
    }

    suspend fun getPhrases(commandId: String): List<String> {
        return dao.getPhrasesByCommandId(commandId).map { it.phrase }
    }

    companion object {
        private const val PARAM_DELIM = "||"

        fun parseActionParams(actionType: String, actionTarget: String): Map<String, String> {
            return when (ActionType.valueOf(actionType)) {
                ActionType.LAUNCH_APP -> mapOf("target" to actionTarget)
                ActionType.OPEN_URL -> mapOf("url" to actionTarget)
                ActionType.SEND_SMS -> {
                    val parts = actionTarget.split(PARAM_DELIM, limit = 2)
                    mapOf("number" to parts[0], "message" to (parts.getOrElse(1) { "" }))
                }
                ActionType.MAKE_CALL -> mapOf("number" to actionTarget)
                ActionType.SET_TIMER -> mapOf("duration" to actionTarget)
                ActionType.SET_ALARM -> mapOf("time" to actionTarget)
                ActionType.TOGGLE_WIFI -> mapOf("toggle" to actionTarget)
                ActionType.TOGGLE_BLUETOOTH -> mapOf("toggle" to actionTarget)
                ActionType.MEDIA_NEXT -> emptyMap()
                ActionType.MEDIA_PREVIOUS -> emptyMap()
                ActionType.MEDIA_STOP -> emptyMap()
                ActionType.MEDIA_START -> emptyMap()
                ActionType.NAVIGATE_TO -> mapOf("place" to actionTarget)
            }
        }

        fun encodeActionParams(actionType: String, param1: String, param2: String): String {
            return when (ActionType.valueOf(actionType)) {
                ActionType.SEND_SMS -> "$param1$PARAM_DELIM$param2"
                ActionType.TOGGLE_WIFI -> "wifi"
                ActionType.TOGGLE_BLUETOOTH -> "bluetooth"
                ActionType.MEDIA_NEXT -> ""
                ActionType.MEDIA_PREVIOUS -> ""
                ActionType.MEDIA_STOP -> ""
                ActionType.MEDIA_START -> ""
                ActionType.NAVIGATE_TO -> param1
                else -> param1
            }
        }
    }
}
