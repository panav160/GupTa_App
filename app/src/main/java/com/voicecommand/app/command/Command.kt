package com.voicecommand.app.command

enum class ActionType {
    LAUNCH_APP,
    OPEN_URL,
    SEND_SMS,
    MAKE_CALL,
    SET_TIMER,
    SET_ALARM,
    TOGGLE_WIFI,
    TOGGLE_BLUETOOTH,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    MEDIA_STOP,
    MEDIA_START,
    NAVIGATE_TO
}

data class Command(
    val id: String,
    val phrases: List<String>,
    val actionType: ActionType,
    val actionParams: Map<String, String> = emptyMap()
)
