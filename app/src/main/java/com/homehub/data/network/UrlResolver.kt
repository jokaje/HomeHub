package com.homehub.data.network

import com.homehub.data.settings.ServiceConfig
import com.homehub.data.settings.ServiceId
import com.homehub.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Http {
    /** Standard-Client für API-Aufrufe. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Kurzer Timeout für Erreichbarkeits-Checks – großzügig genug für
     *  den ersten Verbindungsaufbau (DNS + TLS) nach App-Start. */
    val probeClient: OkHttpClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
}

/**
 * Wählt pro Dienst automatisch die lokale oder die Remote-URL:
 * 1. Lokale URL wird kurz angepingt – antwortet sie, wird sie verwendet.
 * 2. Sonst Fallback auf die Remote-URL.
 * Das Ergebnis wird 30 Sekunden gecacht, damit nicht jeder Request neu probt.
 */
class UrlResolver(private val settings: SettingsRepository) {

    private data class Cached(val url: String, val at: Long)
    private val cache = ConcurrentHashMap<ServiceId, Cached>()

    suspend fun baseUrl(id: ServiceId): String? {
        val cfg = settings.get(id)
        if (!cfg.isConfigured) return null

        cache[id]?.let { if (System.currentTimeMillis() - it.at < 30_000) return it.url }

        val resolved = resolve(cfg)
        if (resolved != null) cache[id] = Cached(resolved, System.currentTimeMillis())
        return resolved
    }

    fun invalidate(id: ServiceId? = null) {
        if (id == null) cache.clear() else cache.remove(id)
    }

    private suspend fun resolve(cfg: ServiceConfig): String? = withContext(Dispatchers.IO) {
        val local = cfg.localUrl.takeIf { it.isNotBlank() }
        val remote = cfg.remoteUrl.takeIf { it.isNotBlank() }
        if (local != null && isReachable(local)) return@withContext local
        if (remote != null && isReachable(remote)) return@withContext remote
        // Nichts erreichbar -> trotzdem beste Vermutung zurückgeben,
        // damit Fehlermeldungen aussagekräftig sind.
        return@withContext local ?: remote
    }

    private fun isReachable(base: String): Boolean = try {
        val req = Request.Builder().url(base).head().build()
        Http.probeClient.newCall(req).execute().use { true } // jede HTTP-Antwort = erreichbar
    } catch (_: Exception) {
        false
    }
}
