package com.homehub.data.jellyfin

import com.homehub.data.network.Http
import com.homehub.data.network.UrlResolver
import com.homehub.data.settings.ServiceId
import com.homehub.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID

/**
 * Jellyfin-Anbindung über die offizielle REST-API.
 * - Basis-URL wird pro Request dynamisch aufgelöst (lokal vs. remote).
 * - Anmeldung per Benutzername/Passwort (AuthenticateByName) → Access-Token.
 *   Token + User-ID werden für die Sitzung im Speicher gehalten.
 * - Jede Anfrage trägt den MediaBrowser-Authorization-Header.
 */
class JellyfinRepository(
    private val settings: SettingsRepository,
    private val urls: UrlResolver
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Stabile Geräte-ID für diese Installation/Sitzung
    private val deviceId: String = UUID.randomUUID().toString().replace("-", "").take(16)

    @Volatile private var accessToken: String? = null
    @Volatile var userId: String? = null
        private set

    private fun username(): String = settings.get(ServiceId.JELLYFIN).extra
    private fun password(): String = settings.get(ServiceId.JELLYFIN).token

    private fun authHeader(): String {
        val base = "MediaBrowser Client=\"HomeHub\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"1.0\""
        return accessToken?.let { "$base, Token=\"$it\"" } ?: base
    }

    private val rewriteInterceptor = Interceptor { chain ->
        val original = chain.request()
        val base = runBlocking { urls.baseUrl(ServiceId.JELLYFIN) }
            ?: throw IllegalStateException("Jellyfin ist nicht konfiguriert.")
        val baseUrl = base.toHttpUrl()
        val url = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .build()
        chain.proceed(
            original.newBuilder()
                .url(url)
                .header("Authorization", authHeader())
                .header("Accept", "application/json")
                .build()
        )
    }

    private val httpClient: OkHttpClient = Http.client.newBuilder()
        .addInterceptor(rewriteInterceptor)
        .build()

    val api: JellyfinApi = Retrofit.Builder()
        .baseUrl("http://jellyfin.placeholder.local/")
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(JellyfinApi::class.java)

    /** Stellt sicher, dass ein gültiges Token + eine User-ID vorliegen. */
    suspend fun ensureAuth(): Boolean {
        if (accessToken != null && userId != null) return true
        if (username().isBlank() || password().isBlank()) return false
        return runCatching {
            val result = api.authenticate(AuthRequest(username(), password()))
            accessToken = result.accessToken.ifBlank { null }
            userId = result.user?.id
            accessToken != null && userId != null
        }.getOrDefault(false)
    }

    fun logout() { accessToken = null; userId = null }

    // --- Wiedergabe-Reporting (für 'Weiterschauen") ---
    private fun msToTicks(ms: Long): Long = ms * 10_000L
    fun ticksToMs(ticks: Long): Long = ticks / 10_000L

    suspend fun reportStart(itemId: String, positionMs: Long) {
        runCatching { api.reportStart(PlaybackStartInfo(itemId, itemId, msToTicks(positionMs))) }
    }

    suspend fun reportProgress(itemId: String, positionMs: Long, paused: Boolean) {
        runCatching { api.reportProgress(PlaybackProgressInfo(itemId, msToTicks(positionMs), paused)) }
    }

    suspend fun reportStopped(itemId: String, positionMs: Long) {
        runCatching { api.reportStopped(PlaybackStopInfo(itemId, msToTicks(positionMs))) }
    }

    /** Original-Datei-URL zum Herunterladen (Offline). */
    suspend fun downloadFileUrl(itemId: String): String? {
        val base = urls.baseUrl(ServiceId.JELLYFIN) ?: return null
        val token = accessToken ?: return null
        return "$base/Items/$itemId/Download?api_key=$token"
    }

    suspend fun setFavorite(itemId: String, favorite: Boolean) {
        val uid = userId ?: return
        runCatching { if (favorite) api.addFavorite(uid, itemId) else api.removeFavorite(uid, itemId) }
    }

    suspend fun markPlayed(itemId: String) {
        val uid = userId ?: return
        runCatching { api.markPlayed(uid, itemId) }
    }

    /** Bild-URL (Primary) inkl. api_key – direkt für Coil nutzbar. */
    suspend fun imageUrl(itemId: String, tag: String? = null, maxWidth: Int = 400): String? {
        val base = urls.baseUrl(ServiceId.JELLYFIN) ?: return null
        val token = accessToken ?: return null
        val tagPart = tag?.let { "&tag=$it" } ?: ""
        return "$base/Items/$itemId/Images/Primary?fillWidth=$maxWidth&quality=90&api_key=$token$tagPart"
    }

    /** Backdrop-URL inkl. api_key. */
    suspend fun backdropUrl(itemId: String, tag: String?, maxWidth: Int = 1000): String? {
        val base = urls.baseUrl(ServiceId.JELLYFIN) ?: return null
        val token = accessToken ?: return null
        if (tag == null) return null
        return "$base/Items/$itemId/Images/Backdrop?fillWidth=$maxWidth&quality=90&tag=$tag&api_key=$token"
    }

    /**
     * Direkte Video-Stream-URL (Direct Play). Funktioniert für die meisten
     * gängigen Formate (MP4/MKV mit H.264/H.265). Bei exotischen Codecs müsste
     * serverseitig transkodiert werden – das ist hier (noch) nicht umgesetzt.
     */
    suspend fun videoStreamUrl(itemId: String): String? {
        val base = urls.baseUrl(ServiceId.JELLYFIN) ?: return null
        val token = accessToken ?: return null
        return "$base/Videos/$itemId/stream?static=true&api_key=$token&DeviceId=$deviceId"
    }

    /**
     * Transkodierte HLS-Stream-URL (maximale Kompatibilität). Wird verwendet,
     * wenn Direct Play fehlschlägt oder für Cast. Der Server transkodiert nach
     * H.264/AAC, das praktisch jedes Gerät und jeder Chromecast abspielen kann.
     */
    suspend fun hlsStreamUrl(itemId: String): String? {
        val base = urls.baseUrl(ServiceId.JELLYFIN) ?: return null
        val token = accessToken ?: return null
        return "$base/Videos/$itemId/master.m3u8?api_key=$token" +
            "&MediaSourceId=$itemId&DeviceId=$deviceId" +
            "&VideoCodec=h264&AudioCodec=aac,mp3" +
            "&MaxStreamingBitrate=8000000&VideoBitrate=8000000&AudioBitrate=192000" +
            "&TranscodingMaxAudioChannels=2&SegmentContainer=ts&MinSegments=1" +
            "&BreakOnNonKeyFrames=true&h264-profile=high&h264-level=41"
    }
}
