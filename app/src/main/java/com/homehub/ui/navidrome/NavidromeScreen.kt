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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
            NavidromeTab.ALBEN -> AlbumGrid(state.albums) { vm.openAlbum(it) }
            NavidromeTab.PLAYLISTS -> PlaylistList(state.playlists, vm)
            NavidromeTab.ARTISTS -> ArtistList(state.artists)
            NavidromeTab.SUCHE -> SearchTab(state, vm)
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
private fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    if (albums.isEmpty()) { EmptyHint("Keine Alben gefunden."); return }
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

@Composable
private fun PlaylistList(playlists: List<Playlist>, vm: NavidromeViewModel) {
    if (playlists.isEmpty()) { EmptyHint("Keine Playlists vorhanden."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(playlists, key = { it.id }) { pl ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    vm.playPlaylist(pl)
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Cover(pl.coverArt, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)), fallback = Icons.Default.QueueMusic)
                Column(Modifier.weight(1f)) {
                    Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pl.songCount} Titel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
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
                SongRow(song, index + 1) { vm.playSongs(state.searchSongs, index) }
            }
        }
    }
}

@Composable
private fun AlbumDetail(album: Album, loading: Boolean, vm: NavidromeViewModel) {
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
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { vm.playSongs(album.songs, 0) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Text("Alle abspielen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (loading && album.songs.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            itemsIndexed(album.songs) { index, song ->
                SongRow(song, song.track ?: (index + 1)) { vm.playSongs(album.songs, index) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SongRow(song: Song, number: Int, onClick: () -> Unit) {
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
    var url by remember(coverArt) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(coverArt) {
        url = com.homehub.core.ServiceLocator.navidrome.coverUrl(coverArt)
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
