package com.homehub.ui.navidrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homehub.playback.MusicPlayer

/** Kompakte Wiedergabeleiste – wird über der unteren Navigation eingeblendet. */
@Composable
fun MiniPlayer(onExpand: () -> Unit) {
    val state by MusicPlayer.state.collectAsState()
    val track = state.currentTrack ?: return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MiniCover(track.coverUrl)
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { MusicPlayer.togglePlayPause() }) {
            Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Wiedergabe", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { MusicPlayer.next() }) {
            Icon(Icons.Default.SkipNext, "Weiter", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MiniCover(url: String?) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Vollbild-Wiedergabe. */
@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val state by MusicPlayer.state.collectAsState()
    val track = state.currentTrack

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Schließen") }
            Text("Wird abgespielt", style = MaterialTheme.typography.titleMedium)
        }

        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nichts in der Wiedergabe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (track.coverUrl != null) {
                AsyncImage(model = track.coverUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))
        val duration = if (state.durationMs > 0) state.durationMs else (track.durationSec * 1000L)
        val pos = state.positionMs.coerceAtMost(duration)
        Slider(
            value = if (duration > 0) pos.toFloat() / duration else 0f,
            onValueChange = { frac -> MusicPlayer.seekTo((frac * duration).toLong()) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(duration), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { MusicPlayer.previous() }) {
                Icon(Icons.Default.SkipPrevious, "Zurück", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                    .clickable { MusicPlayer.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Wiedergabe", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { MusicPlayer.next() }) {
                Icon(Icons.Default.SkipNext, "Weiter", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
