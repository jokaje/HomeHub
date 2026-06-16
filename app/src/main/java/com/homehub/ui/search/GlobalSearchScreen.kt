package com.homehub.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Asset
import com.homehub.data.jellyfin.JellyItem
import com.homehub.data.navidrome.Song

@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onOpenJellyfin: () -> Unit,
    onOpenPhoto: (String) -> Unit
) {
    val vm: GlobalSearchViewModel = viewModel()
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.onQuery(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Alles durchsuchen …") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
        }

        when {
            state.loading && !state.searched ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.searched && state.songs.isEmpty() && state.photos.isEmpty() && state.videos.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Treffer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (state.videos.isNotEmpty()) {
                    item { SectionHeader(Icons.Default.Movie, "Filme & Serien") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(state.videos, key = { it.id }) { v ->
                                VideoResult(v) { if (v.isPlayable) onPlayVideo(v.id) else onOpenJellyfin() }
                            }
                        }
                    }
                }
                if (state.photos.isNotEmpty()) {
                    item { SectionHeader(Icons.Default.Image, "Fotos") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(state.photos, key = { it.id }) { a -> PhotoResult(a) { onOpenPhoto(a.id) } }
                        }
                    }
                }
                if (state.songs.isNotEmpty()) {
                    item { SectionHeader(Icons.Default.MusicNote, "Musik") }
                    itemsIndexed(state.songs) { index, s ->
                        SongResult(s) { vm.playSong(state.songs, index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VideoResult(item: JellyItem, onClick: () -> Unit) {
    var url by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) { url = ServiceLocator.jellyfin.imageUrl(item.id, item.primaryTag, 300) }
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        ThumbBox(url, Modifier.fillMaxWidth().aspectRatio(2f / 3f), Icons.Default.Movie)
        Spacer(Modifier.height(4.dp))
        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = item.seriesName ?: item.type
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PhotoResult(asset: Asset, onClick: () -> Unit) {
    val loader = com.homehub.ui.immich.rememberImmichImageLoader()
    var url by remember(asset.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset.id) { url = ServiceLocator.immich.thumbnailUrl(asset.id) }
    Box(
        Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SongResult(song: Song, onClick: () -> Unit) {
    var url by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) { url = ServiceLocator.navidrome.coverUrl(song.coverArt ?: song.albumId, 120) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThumbBox(url, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), Icons.Default.MusicNote)
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${song.album}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayArrow, "Abspielen", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ThumbBox(url: String?, modifier: Modifier, placeholder: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
