package com.homehub.data.immich

import android.content.ContentResolver
import android.net.Uri
import com.homehub.data.network.Http
import com.homehub.data.network.UrlResolver
import com.homehub.data.settings.ServiceId
import com.homehub.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Zentrale Immich-Anbindung. Die Basis-URL wird pro Request dynamisch
 * aufgelöst (lokal vs. remote), der API-Key automatisch angehängt.
 */
class ImmichRepository(
    private val settings: SettingsRepository,
    private val urls: UrlResolver
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Hängt x-api-key an und tauscht die Platzhalter-Base-URL gegen die aktuell erreichbare. */
    private val rewriteInterceptor = Interceptor { chain ->
        val original = chain.request()
        val base = runBlocking { urls.baseUrl(ServiceId.IMMICH) }
            ?: throw IllegalStateException("Immich ist nicht konfiguriert.")
        val baseUrl = base.toHttpUrl()
        val newUrl = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .build()
        val key = settings.get(ServiceId.IMMICH).token
        chain.proceed(
            original.newBuilder()
                .url(newUrl)
                .header("x-api-key", key)
                .header("Accept", "application/json")
                .build()
        )
    }

    val httpClient: OkHttpClient = Http.client.newBuilder()
        .addInterceptor(rewriteInterceptor)
        .build()

    val api: ImmichApi = Retrofit.Builder()
        .baseUrl("http://immich.placeholder.local/") // wird vom Interceptor ersetzt
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ImmichApi::class.java)

    // ---- Bild-URLs für Coil (Header kommen über den ImageLoader) ----
    suspend fun thumbnailUrl(assetId: String, size: String = "thumbnail"): String? =
        urls.baseUrl(ServiceId.IMMICH)?.let { "$it/api/assets/$assetId/thumbnail?size=$size" }

    suspend fun originalUrl(assetId: String): String? =
        urls.baseUrl(ServiceId.IMMICH)?.let { "$it/api/assets/$assetId/original" }

    suspend fun videoPlaybackUrl(assetId: String): String? =
        urls.baseUrl(ServiceId.IMMICH)?.let { "$it/api/assets/$assetId/video/playback" }

    suspend fun personThumbnailUrl(personId: String): String? =
        urls.baseUrl(ServiceId.IMMICH)?.let { "$it/api/people/$personId/thumbnail" }

    fun apiKey(): String = settings.get(ServiceId.IMMICH).token

    /**
     * Lädt das Original eines Assets in den App-Cache (für "Bild teilen").
     * Gibt die lokale Datei zurück.
     */
    suspend fun downloadOriginal(context: android.content.Context, asset: Asset): Result<java.io.File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = originalUrl(asset.id) ?: error("Immich ist nicht konfiguriert.")
                val shareDir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                // Alte geteilte Dateien aufräumen (älter als 1 Tag)
                shareDir.listFiles()?.forEach {
                    if (System.currentTimeMillis() - it.lastModified() > 86_400_000) it.delete()
                }
                val safeName = asset.originalFileName.ifBlank { "${asset.id}.jpg" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = java.io.File(shareDir, safeName)

                val request = okhttp3.Request.Builder().url(url)
                    .header("x-api-key", apiKey())
                    .build()
                Http.client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("Download fehlgeschlagen: HTTP ${resp.code}")
                    val body = resp.body ?: error("Leere Antwort beim Download.")
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                target
            }
        }

    suspend fun sharedLinkUrl(link: SharedLink): String? =
        urls.baseUrl(ServiceId.IMMICH)?.let { base ->
            // Öffentliche Links sollten über die Remote-URL geteilt werden, falls vorhanden
            val publicBase = settings.get(ServiceId.IMMICH).remoteUrl.ifBlank { base }
            "$publicBase/share/${link.key}"
        }

    // ---- Upload ----
    suspend fun uploadFromUri(
        resolver: ContentResolver,
        uri: Uri,
        fileName: String,
        createdAt: Long,
        deviceId: String
    ): Result<UploadResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Datei konnte nicht gelesen werden: $fileName")
            val mime = resolver.getType(uri) ?: "application/octet-stream"

            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            val created = iso.format(Date(createdAt))

            val filePart = MultipartBody.Part.createFormData(
                "assetData", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            api.upload(
                assetData = filePart,
                deviceAssetId = text("$fileName-$createdAt"),
                deviceId = text(deviceId),
                fileCreatedAt = text(created),
                fileModifiedAt = text(created),
                isFavorite = text("false")
            )
        }
    }

    private fun text(value: String): RequestBody =
        value.toRequestBody("text/plain".toMediaTypeOrNull())
}
