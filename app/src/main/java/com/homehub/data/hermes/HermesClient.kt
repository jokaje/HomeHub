package com.homehub.data.hermes

import com.homehub.data.network.Http
import com.homehub.data.network.UrlResolver
import com.homehub.data.settings.ServiceId
import com.homehub.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ChatMessage(val role: String, val content: String)

sealed interface HermesEvent {
    data class Token(val text: String) : HermesEvent
    data object Done : HermesEvent
    data class Error(val message: String) : HermesEvent
}

/**
 * Spricht mit dem Hermes Agent über die OpenAI-kompatible API
 * (POST {base}/v1/chat/completions, "Authorization: Bearer <key>").
 * Antworten werden gestreamt, damit der Orb live "sprechen" kann.
 */
class HermesClient(
    private val settings: SettingsRepository,
    private val urls: UrlResolver
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun streamChat(history: List<ChatMessage>): Flow<HermesEvent> = callbackFlow {
        val base = urls.baseUrl(ServiceId.HERMES)
        if (base == null) {
            trySend(HermesEvent.Error("Hermes ist nicht konfiguriert. Bitte URL in den Einstellungen hinterlegen."))
            close(); return@callbackFlow
        }
        val cfg = settings.get(ServiceId.HERMES)
        val model = cfg.extra.ifBlank { "default" }

        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                history.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }.toString()

        val reqBuilder = Request.Builder()
            .url("$base/v1/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
        if (cfg.token.isNotBlank()) reqBuilder.header("Authorization", "Bearer ${cfg.token}")

        val call = Http.client.newCall(reqBuilder.build())
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    trySend(HermesEvent.Error("Hermes-Fehler: HTTP ${resp.code}"))
                    close(); return@use
                }
                val source = resp.body?.source()
                if (source == null) {
                    trySend(HermesEvent.Error("Leere Antwort vom Server."))
                    close(); return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    runCatching {
                        val obj = json.parseToJsonElement(payload).jsonObject
                        val delta = obj["choices"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("delta")?.jsonObject
                        val text = delta?.get("content")?.jsonPrimitive?.contentOrNullSafe()
                        if (!text.isNullOrEmpty()) trySend(HermesEvent.Token(text))
                    }
                }
                trySend(HermesEvent.Done)
                close()
            }
        } catch (e: Exception) {
            trySend(HermesEvent.Error(e.message ?: "Verbindung zu Hermes fehlgeschlagen."))
            close()
        }
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this.isString || this.content != "null") this.content else null
