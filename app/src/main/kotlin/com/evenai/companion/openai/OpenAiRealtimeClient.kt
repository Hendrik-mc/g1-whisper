package com.evenai.companion.openai

import android.util.Log
import com.evenai.companion.domain.model.AiChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "OpenAiRealtime"
private const val REALTIME_URL = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-12-17"

/**
 * OpenAI Realtime API WebSocket client.
 * Streams AI responses as text chunks for display on the G1 lens.
 *
 * Source: OpenAI Realtime API documentation.
 * Reddit: "AI delivers answers in 3-6 seconds" — we stream token-by-token
 * so the first chunk appears on-lens within ~1-2 seconds.
 */
class OpenAiRealtimeClient(private val apiKey: String) {

    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _chunks = Channel<AiChunk>(Channel.UNLIMITED)
    val chunks: Flow<AiChunk> = _chunks.receiveAsFlow()

    private var webSocket: WebSocket? = null
    private var sessionActive = false

    // ── Connect / Session init ────────────────────────────────────────────────
    fun connect() {
        val request = Request.Builder()
            .url(REALTIME_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                sessionActive = true
                sendSessionConfig(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerEvent(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                sessionActive = false
                ws.close(1000, null)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                sessionActive = false
                scope.launch {
                    _chunks.send(AiChunk("[Connection lost. Reconnecting…]", isDone = true))
                }
            }
        })
    }

    fun disconnect() {
        sessionActive = false
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    // ── Send audio chunk (LC3 PCM from mic) ───────────────────────────────────
    fun sendAudio(pcmBase64: String) {
        if (!sessionActive) return
        val event = JSONObject().apply {
            put("type", "input_audio_buffer.append")
            put("audio", pcmBase64)
        }
        webSocket?.send(event.toString())
    }

    // ── Commit audio buffer and request response ──────────────────────────────
    fun requestResponse() {
        if (!sessionActive) return
        val commitEvent = JSONObject().apply { put("type", "input_audio_buffer.commit") }
        val responseEvent = JSONObject().apply { put("type", "response.create") }
        webSocket?.send(commitEvent.toString())
        webSocket?.send(responseEvent.toString())
    }

    // ── Send text prompt ──────────────────────────────────────────────────────
    fun sendTextPrompt(prompt: String) {
        if (!sessionActive) return
        val event = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", listOf(
                    mapOf("type" to "input_text", "text" to prompt)
                ).let {
                    val arr = org.json.JSONArray()
                    it.forEach { m ->
                        arr.put(JSONObject().apply {
                            m.forEach { (k, v) -> put(k, v) }
                        })
                    }
                    arr
                })
            })
        }
        webSocket?.send(event.toString())
        requestResponse()
    }

    // ── Handle server events ──────────────────────────────────────────────────
    private fun handleServerEvent(text: String) {
        runCatching {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "response.text.delta" -> {
                    val delta = json.optString("delta", "")
                    if (delta.isNotEmpty()) {
                        scope.launch { _chunks.send(AiChunk(delta)) }
                    }
                }
                "response.text.done" -> {
                    scope.launch { _chunks.send(AiChunk("", isDone = true)) }
                }
                "response.audio_transcript.delta" -> {
                    val delta = json.optString("delta", "")
                    if (delta.isNotEmpty()) {
                        scope.launch { _chunks.send(AiChunk(delta)) }
                    }
                }
                "response.done" -> {
                    scope.launch { _chunks.send(AiChunk("", isDone = true)) }
                }
                "error" -> {
                    val errMsg = json.optJSONObject("error")?.optString("message") ?: "Unknown error"
                    Log.e(TAG, "OpenAI error: $errMsg")
                    scope.launch { _chunks.send(AiChunk("[Error: $errMsg]", isDone = true)) }
                }
                "session.created" -> {
                    Log.d(TAG, "OpenAI Realtime session created")
                }
            }
        }.onFailure { Log.e(TAG, "Failed to parse server event: ${it.message}") }
    }

    // ── Session configuration ─────────────────────────────────────────────────
    private fun sendSessionConfig(ws: WebSocket) {
        val config = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", org.json.JSONArray().apply {
                    put("text")
                    put("audio")
                })
                put("instructions", "You are a concise AI assistant for smart glasses. Keep responses under 5 short sentences. No markdown.")
                put("voice", "alloy")
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("input_audio_transcription", JSONObject().apply {
                    put("model", "whisper-1")
                })
                put("turn_detection", JSONObject().apply {
                    put("type", "server_vad")
                    put("threshold", 0.5)
                    put("prefix_padding_ms", 300)
                    put("silence_duration_ms", 500)
                })
            })
        }
        ws.send(config.toString())
    }
}
