package com.homehub.data.comfy

import com.homehub.data.network.Http
import com.homehub.data.network.UrlResolver
import com.homehub.data.settings.ServiceId
import com.homehub.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID

data class ComfyImage(val filename: String, val subfolder: String, val type: String)

/**
 * Minimaler ComfyUI-Client:
 * - POST /prompt        -> Workflow (API-Format) in die Queue stellen
 * - GET  /history/{id}  -> Status & Ausgabedateien abfragen
 * - GET  /view?...      -> Bild-URL für die Galerie
 *
 * Der Workflow wird als JSON im "API-Format" erwartet (ComfyUI:
 * Einstellungen -> Dev-Mode aktivieren -> "Save (API Format)").
 * Im Workflow kann {{PROMPT}} als Platzhalter stehen; er wird vor dem
 * Absenden durch den eingegebenen Text ersetzt, {{SEED}} durch eine Zufallszahl.
 */
class ComfyClient(
    private val settings: SettingsRepository,
    private val urls: UrlResolver
) {
    private val json = Json { ignoreUnknownKeys = true }
    val clientId: String = UUID.randomUUID().toString()

    suspend fun base(): String? = urls.baseUrl(ServiceId.COMFYUI)

    suspend fun submit(workflowJson: String, promptText: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = base() ?: error("ComfyUI ist nicht konfiguriert.")
                val seed = (0..Int.MAX_VALUE).random().toString()
                // JSON-sicheres Escapen des Prompts (Anführungszeichen entfernen,
                // da der Platzhalter im Workflow bereits in "" steht)
                val escapedPrompt = kotlinx.serialization.json.JsonPrimitive(promptText)
                    .toString().removeSurrounding("\"")
                val prepared = workflowJson
                    .replace("{{PROMPT}}", escapedPrompt)
                    .replace("{{SEED}}", seed)

                // Validieren, dass es JSON ist
                val workflow = json.parseToJsonElement(prepared).jsonObject

                val body = buildJsonObject {
                    put("prompt", workflow)
                    put("client_id", kotlinx.serialization.json.JsonPrimitive(clientId))
                }.toString()

                val req = Request.Builder()
                    .url("$base/prompt")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                Http.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("ComfyUI-Fehler: HTTP ${resp.code} – ${resp.body?.string()?.take(200)}")
                    val obj = json.parseToJsonElement(resp.body!!.string()).jsonObject
                    obj["prompt_id"]?.jsonPrimitive?.content
                        ?: error("Keine prompt_id in der Antwort.")
                }
            }
        }

    /** Pollt die History, bis Ausgaben vorliegen (oder Timeout). */
    suspend fun awaitImages(promptId: String, timeoutMs: Long = 300_000): Result<List<ComfyImage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = base() ?: error("ComfyUI ist nicht konfiguriert.")
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeoutMs) {
                    val req = Request.Builder().url("$base/history/$promptId").get().build()
                    Http.client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val root = json.parseToJsonElement(resp.body!!.string()).jsonObject
                            val entry = root[promptId]?.jsonObject
                            if (entry != null) {
                                val outputs = entry["outputs"]?.jsonObject ?: JsonObject(emptyMap())
                                val images = outputs.values.flatMap { node ->
                                    node.jsonObject["images"]?.jsonArray?.map { img ->
                                        val o = img.jsonObject
                                        ComfyImage(
                                            filename = o["filename"]?.jsonPrimitive?.content ?: "",
                                            subfolder = o["subfolder"]?.jsonPrimitive?.content ?: "",
                                            type = o["type"]?.jsonPrimitive?.content ?: "output"
                                        )
                                    } ?: emptyList()
                                }
                                if (images.isNotEmpty()) return@runCatching images
                                // Eintrag existiert, aber keine Bilder -> evtl. noch in Arbeit oder reiner Text-Workflow
                                val status = entry["status"]?.jsonObject
                                val completed = status?.get("completed")?.jsonPrimitive?.content == "true"
                                if (completed) return@runCatching emptyList()
                            }
                        }
                    }
                    delay(1500)
                }
                error("Zeitüberschreitung – der Workflow läuft womöglich noch auf dem Server.")
            }
        }

    suspend fun imageUrl(img: ComfyImage): String? = base()?.let { b ->
        val f = URLEncoder.encode(img.filename, "UTF-8")
        val s = URLEncoder.encode(img.subfolder, "UTF-8")
        "$b/view?filename=$f&subfolder=$s&type=${img.type}"
    }
}
