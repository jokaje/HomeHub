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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.setValue
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
    val vm: NavidromeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val starred by vm.starred.collectAsState()
    val nvState by vm.state.collectAsState()
    var showPlaylistPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showPlaylistPicker && track != null) {
        AddToPlaylistDialog(
            playlists = nvState.playlists,
            onDismiss = { showPlaylistPicker = false },
            onPick = { pl -> vm.addToPlaylist(pl.id, track.id); showPlaylistPicker = false },
            onCreateNew = { name -> vm.createPlaylist(name, listOf(track.id)); showPlaylistPicker = false }
        )
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Schließen") }
            Text("Wird abgespielt", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (track != null) {
                IconButton(onClick = { showPlaylistPicker = true }) {
                    Icon(Icons.Default.PlaylistAdd, "Zu Playlist hinzufügen")
                }
            }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Favorit
            val isStarred = track.id in starred
            IconButton(onClick = { vm.toggleStar(track.id) }) {
                Icon(
                    if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorit",
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
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

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = { MusicPlayer.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Zufall", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            // Repeat (aus / alle / einer)
            IconButton(onClick = { MusicPlayer.cycleRepeat() }) {
                Icon(
                    if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Wiederholen",
                    tint = if (state.repeat == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<com.homehub.data.navidrome.Playlist>,
    onDismiss: () -> Unit,
    onPick: (com.homehub.data.navidrome.Playlist) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var creating by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Neue Playlist" else "Zu Playlist hinzufügen") },
        text = {
            if (creating) {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    placeholder = { Text("Name der Playlist") }
                )
            } else {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { creating = true }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Neue Playlist erstellen", style = MaterialTheme.typography.titleMedium)
                    }
                    playlists.forEach { pl ->
                        Text(
                            pl.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(pl) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                androidx.compose.material3.TextButton(
                    onClick = { if (name.isNotBlank()) onCreateNew(name.trim()) },
                    enabled = name.isNotBlank()
                ) { Text("Erstellen") }
            } else {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
        dismissButton = {
            if (creating) androidx.compose.material3.TextButton(onClick = { creating = false }) { Text("Zurück") }
        }
    )
}

private fun fmt(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

