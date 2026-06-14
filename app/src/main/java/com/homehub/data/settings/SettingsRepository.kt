package com.homehub.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Alle in HomeHub eingebundenen Dienste. */
enum class ServiceId(val title: String) {
    IMMICH("Immich"),
    HOME_ASSISTANT("Home Assistant"),
    HERMES("Hermes Agent"),
    OPEN_WEBUI("Open WebUI"),
    COMFYUI("ComfyUI"),
    NAVIDROME("Navidrome");
}

/**
 * Konfiguration eines Dienstes.
 * - [localUrl]: Adresse im Heimnetz, z.B. http://192.168.1.10:2283
 * - [remoteUrl]: Adresse von unterwegs, z.B. https://immich.meinedomain.de
 * - [token]: API-Key (Immich, Open WebUI, Hermes) bzw. Long-Lived-Token (Home Assistant)
 * - [extra]: dienstspezifisch, z.B. Modellname bei Hermes
 */
data class ServiceConfig(
    val localUrl: String = "",
    val remoteUrl: String = "",
    val token: String = "",
    val extra: String = ""
) {
    val isConfigured: Boolean get() = localUrl.isNotBlank() || remoteUrl.isNotBlank()
    fun urls(): List<String> = listOf(localUrl, remoteUrl).filter { it.isNotBlank() }
}

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "homehub_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _configs = MutableStateFlow(loadAll())
    val configs: StateFlow<Map<ServiceId, ServiceConfig>> = _configs

    fun get(id: ServiceId): ServiceConfig = _configs.value[id] ?: ServiceConfig()

    fun save(id: ServiceId, config: ServiceConfig) {
        prefs.edit()
            .putString("${id.name}_local", config.localUrl.trim().trimEnd('/'))
            .putString("${id.name}_remote", config.remoteUrl.trim().trimEnd('/'))
            .putString("${id.name}_token", config.token.trim())
            .putString("${id.name}_extra", config.extra.trim())
            .apply()
        _configs.value = loadAll()
    }

    // Auto-Upload-Einstellungen
    var autoUploadEnabled: Boolean
        get() = prefs.getBoolean("auto_upload", false)
        set(v) { prefs.edit().putBoolean("auto_upload", v).apply(); _configs.value = loadAll() }

    var autoUploadVideos: Boolean
        get() = prefs.getBoolean("auto_upload_videos", true)
        set(v) { prefs.edit().putBoolean("auto_upload_videos", v).apply() }

    /**
     * Geräte-Ordner (MediaStore-Bucket-IDs), die gesichert werden sollen.
     * Leere Menge = alle Ordner sichern.
     */
    var autoUploadBuckets: Set<String>
        get() = prefs.getStringSet("auto_upload_buckets", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("auto_upload_buckets", v).apply() }

    /** MediaStore-IDs, die nachweislich schon auf dem Server liegen. */
    var uploadedMediaIds: Set<String>
        get() = prefs.getStringSet("uploaded_media_ids", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("uploaded_media_ids", HashSet(v)).apply() }

    fun markUploaded(ids: Collection<String>) {
        if (ids.isEmpty()) return
        uploadedMediaIds = uploadedMediaIds + ids
    }

    /** SHA-1-Cache pro MediaStore-Eintrag, damit nicht mehrfach gehasht wird. */
    fun cachedSha(mediaId: String): String? = prefs.getString("sha_$mediaId", null)
    fun cacheSha(mediaId: String, sha: String) {
        prefs.edit().putString("sha_$mediaId", sha).apply()
    }

    /** Gewählte Timeline-Ansicht: "MONTH" oder "DAY" – bleibt dauerhaft erhalten. */
    var timelineGranularity: String
        get() = prefs.getString("timeline_granularity", "MONTH") ?: "MONTH"
        set(v) { prefs.edit().putString("timeline_granularity", v).apply() }

    /** Darstellung: "system", "light" oder "dark" – reaktiv, Theme folgt sofort. */
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode
    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    /**
     * Welche bis zu 4 Bereiche in der unteren Leiste erscheinen (neben "Mehr").
     * Reihenfolge = Anzeigereihenfolge. Reaktiv, damit die Leiste sofort folgt.
     */
    private val defaultTabs = listOf("dashboard", "hermes", "immich", "homeassistant")
    private val _bottomTabs = MutableStateFlow(loadBottomTabs())
    val bottomTabs: StateFlow<List<String>> = _bottomTabs

    private fun loadBottomTabs(): List<String> {
        val raw = prefs.getString("bottom_tabs", null) ?: return defaultTabs
        val list = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return list.ifEmpty { defaultTabs }.take(4)
    }

    fun setBottomTabs(routes: List<String>) {
        val limited = routes.distinct().take(4)
        prefs.edit().putString("bottom_tabs", limited.joinToString(",")).apply()
        _bottomTabs.value = limited
    }

    var lastUploadTimestamp: Long
        get() = prefs.getLong("last_upload_ts", 0L)
        set(v) { prefs.edit().putLong("last_upload_ts", v).apply() }

    private fun loadAll(): Map<ServiceId, ServiceConfig> =
        ServiceId.entries.associateWith { id ->
            ServiceConfig(
                localUrl = prefs.getString("${id.name}_local", "") ?: "",
                remoteUrl = prefs.getString("${id.name}_remote", "") ?: "",
                token = prefs.getString("${id.name}_token", "") ?: "",
                extra = prefs.getString("${id.name}_extra", "") ?: ""
            )
        }
}
