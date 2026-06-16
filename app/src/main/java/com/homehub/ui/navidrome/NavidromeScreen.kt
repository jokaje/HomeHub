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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.homehub.data.navidrome.Album
import com.homehub.data.navidrome.Artist
import com.homehub.data.navidrome.Playlist
import com.homehub.data.navidrome.Song

@Composable
fun NavidromeScreen(vm: NavidromeViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!state.configured || state.albums.isEmpty()) vm.refresh()
    }

    if (!state.configured) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Navidrome ist noch nicht eingerichtet.\nTrage in den Einstellungen Adresse, Benutzername und Passwort ein.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Album-Detail liegt über der Übersicht
    state.openAlbum?.let { album ->
        AlbumDetail(album = album, loading = state.loading, vm = vm)
        return
    }
    // Playlist-Detail
    state.openPlaylist?.let { pl ->
        PlaylistDetail(playlist = pl, loading = state.loading, vm = vm)
        return
    }

    // Kurze Rückmeldungen (Playlist erstellt, Fehler …)
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(state.info, state.error) {
        (state.info ?: state.error)?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.dismissMessage()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Musik", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        NavidromeTabs(state.tab) { vm.selectTab(it) }
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        when (state.tab) {
            NavidromeTab.ALBEN -> AlbumGrid(state.albums, vm) { vm.openAlbum(it) }
            NavidromeTab.PLAYLISTS -> PlaylistList(state.playlists, vm)
            NavidromeTab.ARTISTS -> ArtistList(state.artists)
            NavidromeTab.SUCHE -> SearchTab(state, vm)
            NavidromeTab.OFFLINE -> OfflineTab(state, vm)
        }
    }
}

@Composable
private fun NavidromeTabs(selected: NavidromeTab, onSelect: (NavidromeTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TabChip("Alben", selected == NavidromeTab.ALBEN) { onSelect(NavidromeTab.ALBEN) }
        TabChip("Playlists", selected == NavidromeTab.PLAYLISTS) { onSelect(NavidromeTab.PLAYLISTS) }
        TabChip("Künstler", selected == NavidromeTab.ARTISTS) { onSelect(NavidromeTab.ARTISTS) }
        TabChip("Suche", selected == NavidromeTab.SUCHE) { onSelect(NavidromeTab.SUCHE) }
        TabChip("Offline", selected == NavidromeTab.OFFLINE) { onSelect(NavidromeTab.OFFLINE) }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, vm: NavidromeViewModel, onOpen: (Album) -> Unit) {
    Column {
        // Zufallsmix aus der ganzen Bibliothek
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { vm.playRandomMix() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Shuffle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column {
                Text("Zufallsmix", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Zufällige Titel aus deiner Bibliothek", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (albums.isEmpty()) { EmptyHint("Keine Alben gefunden."); return@Column }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Column(Modifier.clickable { onOpen(album) }) {
                    Cover(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(playlists: List<Playlist>, vm: NavidromeViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name -> vm.createPlaylist(name); showCreate = false }
        )
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { showCreate = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Text("Neue Playlist", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (playlists.isEmpty()) {
            item { Text("Noch keine Playlists.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(playlists, key = { it.id }) { pl ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.openPlaylist(pl) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Cover(pl.coverArt, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)), fallback = Icons.Default.QueueMusic)
                Column(Modifier.weight(1f)) {
                    Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pl.songCount} Titel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Name der Playlist") }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Erstellen") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun PlaylistDetail(playlist: Playlist, loading: Boolean, vm: NavidromeViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.closePlaylist() }) { Icon(Icons.Default.ArrowBack, "Zurück") }
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { vm.deletePlaylist(playlist.id) }) {
                Icon(Icons.Default.Delete, "Playlist löschen", tint = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Abspielen", Icons.Default.PlayArrow) { vm.playSongs(playlist.entries, 0) }
                    PillButton("Zufällig", Icons.Default.Shuffle, filled = false) { vm.shufflePlay(playlist.entries) }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (loading && playlist.entries.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            itemsIndexed(playlist.entries) { index, song ->
                SongRow(song, index + 1, onClick = { vm.playSongs(playlist.entries, index) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ArtistList(artists: List<Artist>) {
    if (artists.isEmpty()) { EmptyHint("Keine Künstler gefunden."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(artists, key = { it.id }) { artist ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Cover(artist.coverArt, Modifier.size(54.dp).clip(CircleShape), fallback = Icons.Default.Person)
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${artist.albumCount} Alben", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SearchTab(state: NavidromeUiState, vm: NavidromeViewModel) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it; vm.search(it) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Titel, Album, Künstler…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardActions = KeyboardActions(onSearch = { vm.search(query) })
    )
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.searchAlbums.isNotEmpty()) {
            item { Text("Alben", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            items(state.searchAlbums, key = { "a-" + it.id }) { album ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openAlbum(album) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Cover(album.coverArt, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                    Column {
                        Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.searchSongs.isNotEmpty()) {
            item { Text("Titel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(state.searchSongs) { index, song ->
                SongRow(song, index + 1, onClick = { vm.playSongs(state.searchSongs, index) })
            }
        }
    }
}

@Composable
private fun AlbumDetail(album: Album, loading: Boolean, vm: NavidromeViewModel) {
    val downloads by vm.downloads.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.closeAlbum() }) { Icon(Icons.Default.ArrowBack, "Zurück") }
            Text("Album", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Cover(album.coverArt, Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(album.name, style = MaterialTheme.typography.titleLarge)
                        Text(album.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        album.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { vm.playSongs(album.songs, 0) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                                Text("Abspielen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            // Zufällig
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { vm.shufflePlay(album.songs) },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Shuffle, "Zufällig abspielen", tint = MaterialTheme.colorScheme.onSurface) }
                            // Album herunterladen
                            val allDownloaded = album.songs.isNotEmpty() && album.songs.all { it.id in downloads.downloadedIds }
                            val anyInProgress = album.songs.any { it.id in downloads.inProgress }
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = !anyInProgress) { vm.downloadAlbum(album) },
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    anyInProgress -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    allDownloaded -> Icon(Icons.Default.DownloadDone, "Heruntergeladen", tint = MaterialTheme.colorScheme.primary)
                                    else -> Icon(Icons.Default.Download, "Album herunterladen", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (loading && album.songs.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            itemsIndexed(album.songs) { index, song ->
                SongRow(
                    song, song.track ?: (index + 1),
                    downloaded = song.id in downloads.downloadedIds,
                    downloading = song.id in downloads.inProgress,
                    onClick = { vm.playSongs(album.songs, index) },
                    onDownload = { vm.downloadSong(song) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun OfflineTab(state: NavidromeUiState, vm: NavidromeViewModel) {
    // offlineRevision triggert Neuzeichnen nach dem Löschen
    val rev = state.offlineRevision
    val songs = remember(rev) { vm.offlineSongs() }
    if (songs.isEmpty()) {
        EmptyHint("Noch nichts heruntergeladen.\nTippe in einem Album auf das Download-Symbol, um Titel offline verfügbar zu machen.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(songs) { index, song ->
            SongRow(
                song, index + 1,
                downloaded = true,
                downloading = false,
                onClick = { vm.playSongs(songs, index) },
                onDownload = { vm.deleteDownload(song.id) },
                deleteMode = true
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SongRow(
    song: Song,
    number: Int,
    downloaded: Boolean = false,
    downloading: Boolean = false,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
    deleteMode: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(song.duration), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onDownload != null) {
            when {
                downloading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                deleteMode -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Delete, "Entfernen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloaded -> Icon(Icons.Default.DownloadDone, "Heruntergeladen", tint = MaterialTheme.colorScheme.primary)
                else -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, "Herunterladen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Cover-Bild mit aufgelöster URL (Auth steckt in der URL) und Fallback-Icon. */
@Composable
fun Cover(
    coverArt: String?,
    modifier: Modifier = Modifier,
    fallback: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.MusicNote
) {
    var url by remember(coverArt) {
        mutableStateOf<String?>(com.homehub.core.ServiceLocator.musicDownloads.localCoverUri(coverArt))
    }
    androidx.compose.runtime.LaunchedEffect(coverArt) {
        if (url == null) url = com.homehub.core.ServiceLocator.navidrome.coverUrl(coverArt)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(fallback, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
