package com.homehub.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Ein abspielbarer Titel mit allem, was Player und UI brauchen. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val streamUrl: String,
    val durationSec: Int
)

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val hasTrack: Boolean get() = currentTrack != null
}

/**
 * Zentraler Musik-Player (ExoPlayer). Lebt als Singleton, damit die Wiedergabe
 * über Screen-Wechsel hinweg erhalten bleibt. Der ExoPlayer wird auf dem
 * Main-Thread mit App-Kontext erzeugt.
 */
object MusicPlayer {
    private var exo: ExoPlayer? = null
    val player: ExoPlayer? get() = exo
    private var appContext: Context? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var queue: List<Track> = emptyList()
    private var positionJob: kotlinx.coroutines.Job? = null

    fun init(context: Context) {
        if (exo != null) return
        appContext = context.applicationContext
        val p = ExoPlayer.Builder(context.applicationContext).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = pushState()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushState()
            override fun onPlaybackStateChanged(playbackState: Int) = pushState()
        })
        exo = p
        startPositionUpdates()
    }

    /** Startet den Hintergrund-Service (Benachrichtigung/Sperrbildschirm). */
    private fun ensureService() {
        val ctx = appContext ?: return
        runCatching {
            val intent = android.content.Intent(ctx, PlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                exo?.let { p ->
                    if (p.isPlaying) pushState()
                }
                delay(500)
            }
        }
    }

    private fun pushState() {
        val p = exo ?: return
        _state.value = PlayerState(
            queue = queue,
            currentIndex = p.currentMediaItemIndex.takeIf { queue.isNotEmpty() } ?: -1,
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 } ?: 0L
        )
    }

    /** Spielt eine Warteschlange ab Index [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val p = exo ?: return
        queue = tracks
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setUri(t.streamUrl)
                .setMediaId(t.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.album)
                        .build()
                )
                .build()
        }
        p.setMediaItems(items, startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)), 0L)
        p.prepare()
        p.playWhenReady = true
        ensureService()
        pushState()
    }

    fun togglePlayPause() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() { exo?.seekToNextMediaItem() }
    fun previous() {
        val p = exo ?: return
        if (p.currentPosition > 3000) p.seekTo(0) else p.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { exo?.seekTo(ms); pushState() }
    fun playIndex(index: Int) {
        val p = exo ?: return
        p.seekTo(index, 0L); p.play(); pushState()
    }
}
