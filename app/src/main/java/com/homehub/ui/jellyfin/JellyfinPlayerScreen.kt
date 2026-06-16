package com.homehub.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastContext
import com.homehub.core.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Vollbild-Video-Player für Jellyfin.
 * - Direct Play zuerst; bei Fehler automatischer Wechsel auf transkodiertes HLS.
 * - Setzt die Wiedergabe an der gespeicherten Position fort (Weiterschauen).
 * - Meldet Start / Fortschritt / Stopp an Jellyfin zurück.
 * - Cast: verbindet sich ein Chromecast, übernimmt der CastPlayer (HLS).
 */
@Composable
fun JellyfinPlayerScreen(itemId: String, fromStart: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = ServiceLocator.jellyfin
    val downloads = ServiceLocator.jellyfinDownloads

    var directUrl by remember(itemId) { mutableStateOf<String?>(null) }
    var hlsUrl by remember(itemId) { mutableStateOf<String?>(null) }
    var localUrl by remember(itemId) { mutableStateOf<String?>(null) }
    var title by remember(itemId) { mutableStateOf("") }
    var resumeMs by remember(itemId) { mutableStateOf(0L) }
    var ready by remember(itemId) { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        repo.ensureAuth()
        repo.userId?.let { uid ->
            runCatching { repo.api.getItem(uid, itemId) }.getOrNull()?.let { item ->
                resumeMs = if (fromStart) 0L else repo.ticksToMs(item.userData?.playbackPositionTicks ?: 0L)
                title = item.name
            }
        }
        localUrl = downloads.localVideoUri(itemId)
        directUrl = localUrl ?: repo.videoStreamUrl(itemId)
        hlsUrl = repo.hlsStreamUrl(itemId)
        ready = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val direct = directUrl
        if (!ready || direct == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            var usingHls by remember(itemId) { mutableStateOf(false) }
            var onCast by remember(itemId) { mutableStateOf(false) }

            val exoPlayer = remember(itemId) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(direct))
                    prepare()
                    if (resumeMs > 0) seekTo(resumeMs)
                    playWhenReady = true
                }
            }

            val castPlayer = remember(itemId) {
                runCatching { CastPlayer(CastContext.getSharedInstance(context)) }.getOrNull()
            }

            fun castMediaItem(): MediaItem {
                val uri = hlsUrl ?: direct
                return MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build()
            }

            // Direct-Play-Fehler -> auf HLS umschalten (einmalig)
            DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val hls = hlsUrl
                        if (!usingHls && hls != null) {
                            usingHls = true
                            val pos = exoPlayer.currentPosition
                            exoPlayer.setMediaItem(
                                MediaItem.Builder().setUri(hls).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                            )
                            exoPlayer.prepare()
                            if (pos > 0) exoPlayer.seekTo(pos)
                            exoPlayer.playWhenReady = true
                        }
                    }
                }
                exoPlayer.addListener(listener)
                onDispose {
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                }
            }

            // Cast-Sitzung: Wiedergabe auf den Chromecast übergeben und zurück
            DisposableEffect(castPlayer) {
                if (castPlayer == null) {
                    onDispose { }
                } else {
                    val l = object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {
                            val pos = exoPlayer.currentPosition
                            exoPlayer.playWhenReady = false
                            castPlayer.setMediaItem(castMediaItem(), pos)
                            castPlayer.prepare()
                            castPlayer.playWhenReady = true
                            onCast = true
                        }
                        override fun onCastSessionUnavailable() {
                            val pos = castPlayer.currentPosition
                            if (pos > 0) exoPlayer.seekTo(pos)
                            exoPlayer.playWhenReady = true
                            onCast = false
                        }
                    }
                    castPlayer.setSessionAvailabilityListener(l)
                    onDispose {
                        castPlayer.setSessionAvailabilityListener(null)
                        castPlayer.release()
                    }
                }
            }

            // Reporting an Jellyfin (lokale Position; pausiert während Cast)
            val casting by rememberUpdatedState(onCast)
            LaunchedEffect(exoPlayer) {
                var lastPos = resumeMs
                repo.reportStart(itemId, resumeMs)
                try {
                    while (isActive) {
                        delay(10_000)
                        if (!casting) {
                            lastPos = exoPlayer.currentPosition
                            repo.reportProgress(itemId, lastPos, !exoPlayer.isPlaying)
                        }
                    }
                } finally {
                    repo.reportStopped(itemId, lastPos)
                }
            }

            val activePlayer: Player = if (onCast && castPlayer != null) castPlayer else exoPlayer

            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { useController = true } },
                update = { pv -> pv.player = activePlayer },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Schließen
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)
        ) {
            Icon(Icons.Default.Close, "Schließen", tint = Color.White)
        }
        // Cast (sicher: öffnet nur bei Tippen den Auswahldialog)
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) {
            CastIconButton()
        }
    }
}

/** Cast-Symbol. Öffnet bei Tippen den Geräte-Auswahldialog – komplett gekapselt. */
@Composable
private fun CastIconButton() {
    val context = LocalContext.current
    val available = remember {
        runCatching {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
    if (available) {
        IconButton(onClick = {
            runCatching {
                val cc = CastContext.getSharedInstance(context)
                val selector = cc.mergedSelector ?: return@runCatching
                val dialog = androidx.mediarouter.app.MediaRouteChooserDialog(context)
                dialog.routeSelector = selector
                dialog.show()
            }
        }) {
            Icon(Icons.Default.Cast, "Auf Gerät streamen", tint = Color.White)
        }
    }
}
