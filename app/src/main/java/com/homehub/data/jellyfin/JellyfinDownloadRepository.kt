package com.homehub.data.jellyfin

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.homehub.data.network.Http
import com.homehub.work.JellyfinDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class JellyDownloadsState(
    val downloadedIds: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap()
)

/**
 * Verwaltet Offline-Downloads von Jellyfin-Videos. Die eigentliche Übertragung
 * übernimmt der [JellyfinDownloadWorker] (Foreground-Service, fortsetzbar). Hier
 * werden Downloads angestoßen, Metadaten/Poster gespeichert und der Status
 * (heruntergeladen + laufender Fortschritt) als StateFlow bereitgestellt.
 */
class JellyfinDownloadRepository(
    private val context: Context,
    private val jellyfin: JellyfinRepository
) {
    private val baseDir = File(context.filesDir, "jellyfin").apply { mkdirs() }
    private val videoDir = File(baseDir, "video").apply { mkdirs() }
    private val posterDir = File(baseDir, "poster").apply { mkdirs() }
    private val metaFile = File(baseDir, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val items = mutableListOf<JellyItem>()
    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<JellyDownloadsState> =
        combine(
            _downloaded,
            WorkManager.getInstance(context).getWorkInfosByTagFlow(JellyfinDownloadWorker.TAG)
        ) { downloaded, infos ->
            val progress = mutableMapOf<String, Float>()
            for (info in infos) {
                if (info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED) {
                    val id = info.tags.firstOrNull { it.startsWith("id:") }?.removePrefix("id:")
                    if (id != null) progress[id] = info.progress.getFloat(JellyfinDownloadWorker.KEY_PROGRESS, 0f)
                }
            }
            JellyDownloadsState(downloaded, progress)
        }.stateIn(scope, SharingStarted.Eagerly, JellyDownloadsState())

    init { load() }

    private fun load() {
        runCatching {
            if (metaFile.exists()) {
                items.clear()
                items.addAll(json.decodeFromString<List<JellyItem>>(metaFile.readText()))
            }
        }
        _downloaded.value = items.map { it.id }.toSet()
    }

    private fun persist() {
        runCatching { metaFile.writeText(json.encodeToString(items.toList())) }
        _downloaded.value = items.map { it.id }.toSet()
    }

    // Vom Worker genutzt
    fun videoTarget(id: String) = File(videoDir, id)
    fun partTarget(id: String) = File(videoDir, "$id.part")
    private fun posterFile(id: String) = File(posterDir, "$id.jpg")

    fun markDownloaded(item: JellyItem) {
        synchronized(items) {
            if (items.none { it.id == item.id }) { items.add(item); persist() }
        }
    }

    fun downloadPoster(item: JellyItem) {
        val target = posterFile(item.id)
        if (target.exists()) return
        runCatching {
            kotlinx.coroutines.runBlocking { jellyfin.imageUrl(item.id, item.primaryTag, 600) }?.let { url ->
                Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    r.body?.byteStream()?.use { i -> FileOutputStream(target).use { o -> i.copyTo(o) } }
                }
            }
        }
    }

    fun isDownloaded(id: String): Boolean = videoTarget(id).exists()
    fun localVideoUri(id: String): String? =
        videoTarget(id).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }
    fun localPosterUri(id: String): String? =
        posterFile(id).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }
    fun downloadedItems(): List<JellyItem> = synchronized(items) { items.toList() }

    /** Download anstoßen (läuft als Foreground-Worker, auch wenn die App schließt). */
    fun download(item: JellyItem) {
        if (isDownloaded(item.id)) return
        val request = OneTimeWorkRequestBuilder<JellyfinDownloadWorker>()
            .setInputData(
                workDataOf(
                    JellyfinDownloadWorker.KEY_ID to item.id,
                    JellyfinDownloadWorker.KEY_NAME to item.name,
                    JellyfinDownloadWorker.KEY_ITEM to json.encodeToString(item)
                )
            )
            .addTag(JellyfinDownloadWorker.TAG)
            .addTag(JellyfinDownloadWorker.idTag(item.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "jf_dl_${item.id}", androidx.work.ExistingWorkPolicy.KEEP, request
        )
    }

    fun delete(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("jf_dl_$id")
        videoTarget(id).delete()
        partTarget(id).delete()
        posterFile(id).delete()
        synchronized(items) { items.removeAll { it.id == id } }
        persist()
    }
}
