package com.homehub.data.navidrome

import android.content.Context
import android.net.Uri
import com.homehub.data.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class DownloadsState(
    val downloadedIds: Set<String> = emptySet(),
    val inProgress: Set<String> = emptySet()
)

/**
 * Lädt Titel (Originaldatei) und Cover für die Offline-Wiedergabe herunter und
 * speichert die Metadaten lokal. Heruntergeladene Titel spielen ohne Server.
 *
 * Ablage: filesDir/navidrome/audio/{songId}, /covers/{coverArtId},
 * /downloads.json (Liste der Song-Metadaten).
 */
class MusicDownloadRepository(
    context: Context,
    private val navidrome: NavidromeRepository
) {
    private val baseDir = File(context.filesDir, "navidrome").apply { mkdirs() }
    private val audioDir = File(baseDir, "audio").apply { mkdirs() }
    private val coverDir = File(baseDir, "covers").apply { mkdirs() }
    private val metaFile = File(baseDir, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val songs = mutableListOf<Song>()

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state

    init { load() }

    private fun load() {
        runCatching {
            if (metaFile.exists()) {
                songs.clear()
                songs.addAll(json.decodeFromString<List<Song>>(metaFile.readText()))
            }
        }
        pushState()
    }

    private fun persist() {
        runCatching { metaFile.writeText(json.encodeToString(songs.toList())) }
        pushState()
    }

    private fun pushState() {
        _state.value = _state.value.copy(downloadedIds = songs.map { it.id }.toSet())
    }

    private fun audioFile(id: String) = File(audioDir, id)
    private fun coverFile(coverArt: String) = File(coverDir, coverArt)

    fun isDownloaded(id: String): Boolean = audioFile(id).exists()

    fun localAudioUri(id: String): String? =
        audioFile(id).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }

    fun localCoverUri(coverArt: String?): String? {
        if (coverArt.isNullOrBlank()) return null
        return coverFile(coverArt).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }
    }

    fun downloadedSongs(): List<Song> = songs.sortedBy { it.album + (it.track ?: 0) }

    private fun setInProgress(id: String, on: Boolean) {
        val set = _state.value.inProgress.toMutableSet()
        if (on) set.add(id) else set.remove(id)
        _state.value = _state.value.copy(inProgress = set)
    }

    private suspend fun downloadTo(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out) }
                }
            }
            true
        }.getOrElse { target.delete(); false }
    }

    /** Lädt einen Titel inkl. Cover herunter. */
    suspend fun download(song: Song) {
        if (isDownloaded(song.id) || _state.value.inProgress.contains(song.id)) return
        setInProgress(song.id, true)
        try {
            val url = navidrome.downloadUrl(song.id) ?: return
            val ok = downloadTo(url, audioFile(song.id))
            if (!ok) return
            // Cover (best effort)
            val cover = song.coverArt ?: song.albumId
            if (cover != null && !coverFile(cover).exists()) {
                navidrome.coverUrl(cover, 600)?.let { downloadTo(it, coverFile(cover)) }
            }
            if (songs.none { it.id == song.id }) {
                songs.add(song.copy(coverArt = cover))
                persist()
            }
        } finally {
            setInProgress(song.id, false)
        }
    }

    /** Lädt mehrere Titel nacheinander herunter (z. B. ein Album). */
    suspend fun downloadAll(list: List<Song>) {
        for (s in list) download(s)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        audioFile(id).delete()
        songs.removeAll { it.id == id }
        persist()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        audioDir.listFiles()?.forEach { it.delete() }
        coverDir.listFiles()?.forEach { it.delete() }
        songs.clear()
        persist()
    }
}
