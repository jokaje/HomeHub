package com.homehub.data.navidrome

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
import java.security.MessageDigest
import java.util.UUID

/**
 * Navidrome-Anbindung über die Subsonic-API.
 * - Basis-URL wird pro Request dynamisch aufgelöst (lokal vs. remote).
 * - Authentifizierung per Token-Verfahren: t = md5(passwort + salt), s = salt.
 *   So wird das Passwort nie im Klartext übertragen.
 */
class NavidromeRepository(
    private val settings: SettingsRepository,
    private val urls: UrlResolver
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Zufälliger Salt pro App-Sitzung
    private val salt: String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun username(): String = settings.get(ServiceId.NAVIDROME).extra.orEmpty()
    private fun password(): String = settings.get(ServiceId.NAVIDROME).token

    /** Authentifizierungs-Parameter, die an jede Subsonic-Anfrage angehängt werden. */
    private fun authParams(): Map<String, String> = mapOf(
        "u" to username(),
        "t" to md5(password() + salt),
        "s" to salt,
        "v" to "1.16.1",
        "c" to "HomeHub",
        "f" to "json"
    )

    private val rewriteInterceptor = Interceptor { chain ->
        val original = chain.request()
        val base = runBlocking { urls.baseUrl(ServiceId.NAVIDROME) }
            ?: throw IllegalStateException("Navidrome ist nicht konfiguriert.")
        val baseUrl = base.toHttpUrl()
        val builder = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
        authParams().forEach { (k, v) -> builder.addQueryParameter(k, v) }
        chain.proceed(
            original.newBuilder()
                .url(builder.build())
                .header("Accept", "application/json")
                .build()
        )
    }

    val httpClient: OkHttpClient = Http.client.newBuilder()
        .addInterceptor(rewriteInterceptor)
        .build()

    val api: SubsonicApi = Retrofit.Builder()
        .baseUrl("http://navidrome.placeholder.local/") // wird vom Interceptor ersetzt
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SubsonicApi::class.java)

    /** Voll aufgelöste Stream-URL inkl. Auth – direkt für ExoPlayer nutzbar. */
    suspend fun streamUrl(id: String): String? {
        val base = urls.baseUrl(ServiceId.NAVIDROME) ?: return null
        val query = authParams().entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/stream.view?id=$id&$query"
    }

    /** Voll aufgelöste Download-URL (Originaldatei) inkl. Auth – für Offline-Downloads. */
    suspend fun downloadUrl(id: String): String? {
        val base = urls.baseUrl(ServiceId.NAVIDROME) ?: return null
        val query = authParams().entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/download.view?id=$id&$query"
    }

    /** Voll aufgelöste Cover-URL inkl. Auth – direkt für Coil nutzbar. */
    suspend fun coverUrl(coverArtId: String?, size: Int = 300): String? {
        if (coverArtId.isNullOrBlank()) return null
        val base = urls.baseUrl(ServiceId.NAVIDROME) ?: return null
        val query = authParams().entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/getCoverArt.view?id=$coverArtId&size=$size&$query"
    }
}
