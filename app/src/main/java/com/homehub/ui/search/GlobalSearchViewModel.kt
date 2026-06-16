package com.homehub.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Asset
import com.homehub.data.immich.SmartSearchRequest
import com.homehub.data.jellyfin.JellyItem
import com.homehub.data.navidrome.Song
import com.homehub.data.settings.ServiceId
import com.homehub.playback.MusicPlayer
import com.homehub.playback.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlobalSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val searched: Boolean = false,
    val songs: List<Song> = emptyList(),
    val photos: List<Asset> = emptyList(),
    val videos: List<JellyItem> = emptyList()
)

/** Sucht parallel über alle Module: Navidrome (Musik), Immich (Fotos), Jellyfin (Video). */
class GlobalSearchViewModel : ViewModel() {
    private val _state = MutableStateFlow(GlobalSearchState())
    val state: StateFlow<GlobalSearchState> = _state
    private var job: Job? = null

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        job?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(songs = emptyList(), photos = emptyList(), videos = emptyList(), searched = false, loading = false) }
            return
        }
        job = viewModelScope.launch {
            delay(320) // Debounce
            _state.update { it.copy(loading = true) }
            coroutineScope {
                val music = async { searchMusic(q) }
                val photos = async { searchPhotos(q) }
                val videos = async { searchVideos(q) }
                _state.update {
                    it.copy(loading = false, searched = true, songs = music.await(), photos = photos.await(), videos = videos.await())
                }
            }
        }
    }

    private suspend fun searchMusic(q: String): List<Song> {
        if (!ServiceLocator.settings.get(ServiceId.NAVIDROME).isConfigured) return emptyList()
        return runCatching {
            ServiceLocator.navidrome.api.search3(q).response.searchResult3?.song.orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun searchPhotos(q: String): List<Asset> {
        if (!ServiceLocator.settings.get(ServiceId.IMMICH).isConfigured) return emptyList()
        return runCatching {
            ServiceLocator.immich.api.smartSearch(SmartSearchRequest(query = q, size = 60)).assets.items
        }.getOrDefault(emptyList())
    }

    private suspend fun searchVideos(q: String): List<JellyItem> {
        if (!ServiceLocator.settings.get(ServiceId.JELLYFIN).isConfigured) return emptyList()
        if (!ServiceLocator.jellyfin.ensureAuth()) return emptyList()
        val uid = ServiceLocator.jellyfin.userId ?: return emptyList()
        return runCatching {
            ServiceLocator.jellyfin.api.getItems(
                uid, includeItemTypes = "Movie,Series,Episode", recursive = true, searchTerm = q, limit = 40
            ).items
        }.getOrDefault(emptyList())
    }

    fun playSong(songs: List<Song>, startIndex: Int) = viewModelScope.launch {
        val dl = ServiceLocator.musicDownloads
        val repo = ServiceLocator.navidrome
        val tracks = songs.mapNotNull { s ->
            val cover = s.coverArt ?: s.albumId
            val url = dl.localAudioUri(s.id) ?: repo.streamUrl(s.id) ?: return@mapNotNull null
            Track(s.id, s.title, s.artist, s.album, dl.localCoverUri(cover) ?: repo.coverUrl(cover), url, s.duration)
        }
        if (tracks.isNotEmpty()) MusicPlayer.playQueue(tracks, startIndex.coerceIn(0, tracks.size - 1))
    }
}
