package com.homehub.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = 0
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val hasTrack: Boolean get() = currentTrack != null
}

/**
 * Zentraler Musik-Player. Verbindet sich per [MediaController] mit dem
 * [PlaybackService], der den eigentlichen ExoPlayer besitzt. Dadurch übernimmt
 * Media3 die Hintergrundwiedergabe, Benachrichtigung und Vordergrund-Logik
 * automatisch – wir müssen den Service nicht manuell in den Vordergrund holen.
 */
object MusicPlayer {
    private var appContext: Context? = null
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var queue: List<Track> = emptyList()
    private var pending: Pair<List<Track>, Int>? = null
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()
    }

    /** Verbindung zum Wiedergabe-Service aufbauen (asynchron). */
    fun init(context: Context) {
        if (controller != null || future != null) return
        appContext = context.applicationContext
        val token = SessionToken(appContext!!, ComponentName(appContext!!, PlaybackService::class.java))
        val f = MediaController.Builder(appContext!!, token).buildAsync()
        future = f
        f.addListener({
            controller = runCatching { f.get() }.getOrNull()
            controller?.addListener(listener)
            startPositionUpdates()
            // Falls vor dem Verbinden schon etwas gestartet wurde: nachholen
            pending?.let { (tracks, index) -> pending = null; playQueue(tracks, index) }
            pushState()
        }, ContextCompat.getMainExecutor(appContext!!))
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                controller?.let { if (it.isPlaying) pushState() }
                delay(500)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: return
        _state.value = PlayerState(
            queue = queue,
            currentIndex = if (queue.isEmpty()) -1 else c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode
        )
    }

    /** Spielt eine Warteschlange ab Index [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller
        if (c == null) { pending = tracks to startIndex; return }
        if (tracks.isEmpty()) return
        queue = tracks
        val items = tracks.map { t ->
            val md = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
            t.coverUrl?.let { md.setArtworkUri(android.net.Uri.parse(it)) }
            MediaItem.Builder()
                .setUri(t.streamUrl)
                .setMediaId(t.id)
                .setMediaMetadata(md.build())
                .build()
        }
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(items, startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
        pushState()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        pushState()
    }

    /** Wiederholung durchschalten: aus → alle → einer → aus. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        pushState()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms); pushState() }

    fun playIndex(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L); c.play(); pushState()
    }
}
