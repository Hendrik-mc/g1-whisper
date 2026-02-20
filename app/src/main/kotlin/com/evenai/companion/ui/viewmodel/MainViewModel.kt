package com.evenai.companion.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evenai.companion.BuildConfig
import com.evenai.companion.data.repository.GlassesRepository
import com.evenai.companion.data.repository.PreferencesRepository
import com.evenai.companion.domain.model.AiChunk
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.domain.model.LensPage
import com.evenai.companion.domain.model.Widget
import com.evenai.companion.openai.OpenAiRealtimeClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    val glassesRepository: GlassesRepository,
    val prefsRepository: PreferencesRepository
) : AndroidViewModel(application) {

    // ── OpenAI client ─────────────────────────────────────────────────────────
    private val openAi = OpenAiRealtimeClient(BuildConfig.OPENAI_API_KEY)
    private var aiResponseBuffer = StringBuilder()
    private var displayJob: Job? = null

    // ── Glasses state ─────────────────────────────────────────────────────────
    val glassesState: StateFlow<GlassesState> = glassesRepository.glassesState

    val currentPage: StateFlow<LensPage?> = glassesRepository.currentPage

    // ── Widget selection ──────────────────────────────────────────────────────
    val activeWidget: StateFlow<Widget> = prefsRepository.activeWidget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Widget.AI_ASSISTANT)

    private val _widgetSwitching = MutableStateFlow(false)
    val widgetSwitching: StateFlow<Boolean> = _widgetSwitching.asStateFlow()

    // ── Mic state ─────────────────────────────────────────────────────────────
    private val _micActive = MutableStateFlow(false)
    val micActive: StateFlow<Boolean> = _micActive.asStateFlow()

    // ── Developer mode ────────────────────────────────────────────────────────
    val developerMode: StateFlow<Boolean> = prefsRepository.developerModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Onboarding ────────────────────────────────────────────────────────────
    val onboardingDone: StateFlow<Boolean> = prefsRepository.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Watch for out-of-sync state and surface it to UI
        viewModelScope.launch {
            glassesState.collect { state ->
                if (state is GlassesState.OutOfSync) {
                    // ResyncScreen will pick this up via glassesState
                }
            }
        }

        // Connect OpenAI and collect streaming chunks
        openAi.connect()
        viewModelScope.launch {
            openAi.chunks.collect { chunk -> handleAiChunk(chunk) }
        }
    }

    // ── BLE actions ───────────────────────────────────────────────────────────
    fun startScan()   = glassesRepository.startScan()
    fun stopScan()    = glassesRepository.stopScan()
    fun disconnect()  = glassesRepository.disconnect()
    fun reconnect()   = glassesRepository.reconnect()

    fun resync() {
        viewModelScope.launch { glassesRepository.resync() }
    }

    // ── Widget switching ──────────────────────────────────────────────────────
    // Source: Reddit — 2s debounce for HUD cooldown after double-tap
    fun switchWidget(widget: Widget) {
        if (activeWidget.value == widget) return
        _widgetSwitching.value = true
        viewModelScope.launch {
            prefsRepository.setActiveWidget(widget)
            stopMic()
            // Send widget intro text with debounce
            val introText = widgetIntroText(widget)
            glassesRepository.displayText(introText, debounce = true)
            _widgetSwitching.value = false
        }
    }

    // ── Mic ───────────────────────────────────────────────────────────────────
    fun startMic() {
        viewModelScope.launch {
            val ok = glassesRepository.setMicEnabled(true)
            if (ok) {
                _micActive.value = true
                openAi.requestResponse()
            }
        }
    }

    fun stopMic() {
        viewModelScope.launch {
            glassesRepository.setMicEnabled(false)
            _micActive.value = false
        }
    }

    // ── Text prompt ───────────────────────────────────────────────────────────
    fun sendPrompt(prompt: String) {
        aiResponseBuffer.clear()
        openAi.sendTextPrompt(prompt)
    }

    // ── AI chunk handler ──────────────────────────────────────────────────────
    private fun handleAiChunk(chunk: AiChunk) {
        aiResponseBuffer.append(chunk.text)
        if (chunk.isDone) {
            val finalText = aiResponseBuffer.toString().trim()
            aiResponseBuffer.clear()
            displayJob?.cancel()
            displayJob = viewModelScope.launch {
                glassesRepository.displayText(finalText)
            }
        } else {
            // Stream intermediate text as it arrives
            displayJob?.cancel()
            displayJob = viewModelScope.launch {
                glassesRepository.displayText(aiResponseBuffer.toString())
            }
        }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────
    fun completeOnboarding() {
        viewModelScope.launch { prefsRepository.setOnboardingDone() }
    }

    // ── Developer mode ────────────────────────────────────────────────────────
    fun toggleDeveloperMode() {
        viewModelScope.launch { prefsRepository.toggleDeveloperMode() }
    }

    // ── Widget intro text ─────────────────────────────────────────────────────
    private fun widgetIntroText(widget: Widget): String = when (widget) {
        Widget.AI_ASSISTANT -> "AI Assistant ready. Press the touchbar and speak."
        Widget.LIVE_CAPTION -> "Live Captions active. Transcribing speech…"
        Widget.TRANSLATOR   -> "Translator ready. Speak in any language."
        Widget.TELEPROMPTER -> "Teleprompter active. Load a script to begin."
        Widget.NAVIGATION   -> "Navigation ready. Say a destination."
    }

    override fun onCleared() {
        super.onCleared()
        openAi.disconnect()
        glassesRepository.disconnect()
    }
}
