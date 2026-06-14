package com.homehub.ui.navidrome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehub.core.ServiceLocator
import com.homehub.data.navidrome.Album
import com.homehub.data.navidrome.Artist
import com.homehub.data.navidrome.Playlist
import com.homehub.data.navidrome.Song
import com.homehub.data.settings.ServiceId
import com.homehub.playback.MusicPlayer
import com.homehub.playback.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavidromeTab { ALBEN, PLAYLISTS, ARTISTS, SUCHE }

data class NavidromeUiState(
    val configured: Boolean = true,
    val loading: Boolean = false,
    val tab: NavidromeTab = NavidromeTab.ALBEN,
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val searchAlbums: List<Album> = emptyList(),
    val searchSongs: List<Song> = emptyList(),
    val openAlbum: Album? = null,
    val error: String? = null
)

class NavidromeViewModel : ViewModel() {
    private val repo get() = ServiceLocator.navidrome

    private val _state = MutableStateFlow(NavidromeUiState())
    val state: StateFlow<NavidromeUiState> = _state

    val playerState = MusicPlayer.state

    init { refresh() }

    fun selectTab(tab: NavidromeTab) {
        _state.update { it.copy(tab = tab) }
        when (tab) {
            NavidromeTab.PLAYLISTS -> if (_state.value.playlists.isEmpty()) loadPlaylists()
            NavidromeTab.ARTISTS -> if (_state.value.artists.isEmpty()) loadArtists()
            else -> {}
        }
    }

    fun refresh() {
        if (!ServiceLocator.settings.get(ServiceId.NAVIDROME).isConfigured) {
            _state.update { it.copy(configured = false) }; return
        }
        _state.update { it.copy(configured = true) }
        loadAlbums()
    }

    private fun loadAlbums() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.api.getAlbumList2(type = "newest", size = 60).response }
            .onSuccess { r ->
                if (r.error != null) _state.update { it.copy(loading = false, error = r.error.message) }
                else _state.update { it.copy(loading = false, albums = r.albumList2?.album.orEmpty()) }
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
    }

    private fun loadPlaylists() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.api.getPlaylists().response }
            .onSuccess { r -> _state.update { it.copy(loading = false, playlists = r.playlists?.playlist.orEmpty()) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
    }

    private fun loadArtists() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.api.getArtists().response }
            .onSuccess { r ->
                val all = r.artists?.index.orEmpty().flatMap { it.artist }
                _state.update { it.copy(loading = false, artists = all) }
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.isBlank()) {
            _state.update { it.copy(searchAlbums = emptyList(), searchSongs = emptyList()) }
            return@launch
        }
        runCatching { repo.api.search3(query).response }
            .onSuccess { r ->
                _state.update {
                    it.copy(
                        searchAlbums = r.searchResult3?.album.orEmpty(),
                        searchSongs = r.searchResult3?.song.orEmpty()
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    /** Album mit Titelliste laden und im Detail anzeigen. */
    fun openAlbum(album: Album) = viewModelScope.launch {
        _state.update { it.copy(openAlbum = album.copy(songs = emptyList()), loading = true) }
        runCatching { repo.api.getAlbum(album.id).response }
            .onSuccess { r -> _state.update { it.copy(loading = false, openAlbum = r.album ?: album) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
    }

    fun closeAlbum() = _state.update { it.copy(openAlbum = null) }

    /** Playlist laden und sofort abspielen. */
    fun playPlaylist(playlist: Playlist) = viewModelScope.launch {
        runCatching { repo.api.getPlaylist(playlist.id).response }
            .onSuccess { r ->
                val songs = r.playlist?.entries.orEmpty()
                if (songs.isNotEmpty()) playSongs(songs, 0)
            }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    /** Songs als Warteschlange abspielen (Stream-URLs werden vorab aufgelöst). */
    fun playSongs(songs: List<Song>, startIndex: Int) = viewModelScope.launch {
        if (songs.isEmpty()) return@launch
        val tracks = songs.mapNotNull { s ->
            val url = repo.streamUrl(s.id) ?: return@mapNotNull null
            Track(
                id = s.id,
                title = s.title,
                artist = s.artist,
                album = s.album,
                coverUrl = repo.coverUrl(s.coverArt ?: s.albumId),
                streamUrl = url,
                durationSec = s.duration
            )
        }
        MusicPlayer.playQueue(tracks, startIndex)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Navidrome nicht erreichbar. Läuft der Server?"
        is java.net.SocketTimeoutException -> "Zeitüberschreitung bei der Verbindung zu Navidrome."
        else -> e.message ?: "Unbekannter Fehler"
    }
}
