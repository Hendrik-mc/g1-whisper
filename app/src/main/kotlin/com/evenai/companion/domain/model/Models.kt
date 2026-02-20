package com.evenai.companion.domain.model

/**
 * Five AI widgets — only one active at a time on the dashboard.
 */
enum class Widget(val displayName: String, val description: String, val icon: String) {
    AI_ASSISTANT("AI Assistant",    "Ask anything, hands-free",        "smart_toy"),
    LIVE_CAPTION("Live Captions",   "Transcribe speech in real time",  "closed_caption"),
    TRANSLATOR  ("Translator",      "Speak, get instant translation",  "translate"),
    TELEPROMPTER("Teleprompter",    "Read your script naturally",      "article"),
    NAVIGATION  ("Navigation",      "Turn-by-turn on your lens",       "navigation")
}

/**
 * Current connection state of the glasses pair.
 */
sealed class GlassesState {
    object Disconnected : GlassesState()
    object Scanning     : GlassesState()
    object Connecting   : GlassesState()
    object Connected    : GlassesState()  // at least one arm connected
    object Ready        : GlassesState()  // both arms connected and handshake complete
    object OutOfSync    : GlassesState()  // arms connected but displays mismatched
    data class Error(val message: String) : GlassesState()
}

/**
 * A page of text as displayed on the G1 lens.
 */
data class LensPage(
    val lines: List<String>,
    val pageIndex: Int,
    val totalPages: Int
)

/**
 * A streaming AI response chunk.
 */
data class AiChunk(
    val text: String,
    val isDone: Boolean = false
)

/**
 * User preference for privacy controls.
 */
data class PrivacySettings(
    val microphoneEnabled: Boolean = false,
    val dataRetentionEnabled: Boolean = false
)

/**
 * Developer-mode discovered BLE characteristic.
 */
data class DiscoveredCharacteristic(
    val serviceUuid: String,
    val charUuid: String,
    val properties: Int,
    val propertiesHuman: String
)

/**
 * Onboarding step progression.
 */
enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    PAIRING,
    TUTORIAL,
    DONE
}
