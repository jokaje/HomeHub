package com.homehub.ui.jellyfin

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.homehub.core.ServiceLocator
import com.homehub.data.jellyfin.JellyDownloadsState
import com.homehub.data.jellyfin.JellyItem
import com.homehub.ui.theme.SpotViolet

@Composable
fun JellyfinScreen(onPlay: (String, Boolean) -> Unit) {
    val vm: JellyfinViewModel = viewModel()
    val state by vm.state.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.dismissError()
        }
    }

    when {
        !state.configured ->
            Hint("Jellyfin ist noch nicht eingerichtet.\nTrage in den Einstellungen Adresse, Benutzer und Passwort ein.")
        state.authFailed ->
            Hint("Anmeldung fehlgeschlagen.\nPrüfe Benutzername und Passwort in den Einstellungen.")
        state.showSearch ->
            SearchView(state, vm, onPlay)
        state.showDownloads ->
            DownloadsView(vm, downloads)
        state.openDetail != null ->
            DetailView(state.openDetail!!, state, downloads, vm, onPlay)
        state.openParent != null ->
            ParentView(state.openParent!!, state.openItems, state.loading, vm)
        else ->
            HomeContent(state, downloads, vm, onPlay)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: JellyfinUiState,
    downloads: JellyDownloadsState,
    vm: JellyfinViewModel,
    onPlay: (String, Boolean) -> Unit
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            if (state.featured.isNotEmpty()) {
                HeroCarousel(state.featured, vm, onPlay)
            } else if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            if (downloads.downloadedIds.isNotEmpty()) {
                val items = remember(downloads.downloadedIds) { vm.downloadedItems() }
                MediaRowSection("Downloads", items, landscape = false, onHeaderClick = { vm.openDownloads() }, onClick = { vm.openDetail(it) })
            }

            if (state.resume.isNotEmpty()) {
                ContinueWatchingRow("Weiter ansehen", state.resume, downloads, vm, onPlay)
            }
            if (state.nextUp.isNotEmpty()) {
                ContinueWatchingRow("Nächste Folge", state.nextUp, downloads, vm, onPlay)
            }
            if (state.top10.isNotEmpty()) {
                RankedRow("Top 10", state.top10) { vm.openDetail(it) }
            }

            state.rows.forEach { row ->
                MediaRowSection(row.title, row.items, landscape = row.landscape) { vm.openDetail(it) }
            }

            if (state.views.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowHeader("Bibliotheken")
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.views.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { view -> LibraryTile(view, Modifier.weight(1f)) { vm.openParent(view) } }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeroCarousel(items: List<JellyItem>, vm: JellyfinViewModel, onPlay: (String, Boolean) -> Unit) {
    val pager = rememberPagerState(pageCount = { items.size })
    Box {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(420.dp)) { page ->
            HeroSlide(items[page], vm, onPlay)
        }
        // Such-Symbol oben rechts (Apple-TV-Stil)
        IconButton(
            onClick = { vm.openSearch() },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                .size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
        ) { Icon(Icons.Default.Search, "Suche", tint = Color.White) }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(items.size) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier.size(if (active) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (active) Color.White else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun HeroSlide(item: JellyItem, vm: JellyfinViewModel, onPlay: (String, Boolean) -> Unit) {
    var url by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        url = ServiceLocator.jellyfin.backdropUrl(item.id, item.backdropImageTags?.firstOrNull())
            ?: ServiceLocator.jellyfin.imageUrl(item.id, item.primaryTag, 1000)
    }
    Box(Modifier.fillMaxSize().clickable { vm.openDetail(item) }) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                item.seriesName,
                if (item.type == "Series") "Serie" else if (item.type == "Movie") "Film" else null,
                item.productionYear?.toString()
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable { onPlay(item.id, false) }.padding(horizontal = 26.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    Text("Wiedergeben", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                }
                val fav = item.userData?.isFavorite == true
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                        .clickable { vm.toggleFavorite(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (fav) Icons.Default.Check else Icons.Default.Add, "Zur Liste", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RowHeader(title: String, onClick: (() -> Unit)? = null) {
    Text(
        title, style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    )
}

@Composable
private fun MediaRowSection(title: String, items: List<JellyItem>, landscape: Boolean, onHeaderClick: (() -> Unit)? = null, onClick: (JellyItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowHeader(title, onHeaderClick)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items, key = { it.id }) { item -> MediaCard(item, landscape) { onClick(item) } }
        }
    }
}

/** Top-10-Reihe mit großen Ranking-Zahlen (Apple-TV-/Netflix-Optik). */
@Composable
private fun RankedRow(title: String, items: List<JellyItem>, onClick: (JellyItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(10), key = { it.id }) { item ->
                val rank = items.indexOf(item) + 1
                Box(Modifier.width(150.dp).height(150.dp).clickable { onClick(item) }, contentAlignment = Alignment.BottomStart) {
                    Text(
                        rank.toString(),
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        maxLines = 1
                    )
                    Poster(item, Modifier.width(96.dp).aspectRatio(2f / 3f).align(Alignment.BottomEnd), false)
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<JellyItem>,
    downloads: JellyDownloadsState,
    vm: JellyfinViewModel,
    onPlay: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items, key = { it.id }) { item ->
                Column(Modifier.width(250.dp)) {
                    Box(Modifier.clickable { onPlay(item.id, false) }) {
                        Poster(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f), true)
                        val frac = progressFraction(item)
                        if (frac > 0f) {
                            Box(
                                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp)
                                    .background(Color.Black.copy(alpha = 0.45f))
                            ) {
                                Box(Modifier.fillMaxWidth(frac).height(5.dp).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(continueLabel(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CardMenu(item, downloads, vm, onPlay)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardMenu(item: JellyItem, downloads: JellyDownloadsState, vm: JellyfinViewModel, onPlay: (String, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.MoreVert, "Mehr", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Von vorne abspielen") }, onClick = { open = false; onPlay(item.id, true) },
                leadingIcon = { Icon(Icons.Default.Replay, null) })
            val downloaded = item.id in downloads.downloadedIds
            val inProgress = downloads.progress.containsKey(item.id)
            DropdownMenuItem(
                text = { Text(if (downloaded) "Download entfernen" else if (inProgress) "Lädt …" else "Herunterladen") },
                onClick = { open = false; if (downloaded) vm.deleteDownload(item.id) else if (!inProgress) vm.download(item) },
                leadingIcon = { Icon(if (downloaded) Icons.Default.DownloadDone else Icons.Default.Download, null) }
            )
        }
    }
}

@Composable
private fun MediaCard(item: JellyItem, landscape: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(if (landscape) 230.dp else 130.dp).clickable(onClick = onClick)) {
        Poster(item, Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f), landscape)
        Spacer(Modifier.height(6.dp))
        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = item.seriesName ?: item.productionYear?.toString().orEmpty()
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun progressFraction(item: JellyItem): Float {
    val pct = item.userData?.playedPercentage
    if (pct != null && pct > 0) return (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val pos = item.userData?.playbackPositionTicks ?: 0L
    val total = item.runTimeTicks ?: 0L
    return if (total > 0 && pos > 0) (pos.toFloat() / total).coerceIn(0f, 1f) else 0f
}

private fun continueLabel(item: JellyItem): String {
    val remainingMin = run {
        val total = item.runTimeTicks ?: 0L
        val pos = item.userData?.playbackPositionTicks ?: 0L
        if (total > pos) ((total - pos) / 600_000_000L).toInt() else 0
    }
    val ep = if (item.type == "Episode") {
        listOfNotNull(item.parentIndexNumber?.let { "S$it" }, item.indexNumber?.let { "F$it" }).joinToString("")
    } else ""
    val parts = mutableListOf<String>()
    if (ep.isNotBlank()) parts.add(ep)
    if (remainingMin > 0) parts.add("$remainingMin m") else parts.add(item.name)
    return parts.joinToString(" · ")
}

@Composable
private fun LibraryTile(view: JellyItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(96.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(16.dp)
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("BIBLIOTHEK", style = MaterialTheme.typography.labelMedium, color = SpotViolet)
            Text(view.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchView(state: JellyfinUiState, vm: JellyfinViewModel, onPlay: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.onSearchQuery(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Filme, Serien, Episoden …") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
        }
        if (state.searching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (state.searchResults.isEmpty() && state.searchQuery.isNotBlank() && !state.searching) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Keine Treffer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(state.searchResults, key = { it.id }) { item ->
                    Column(Modifier.clickable {
                        if (item.isPlayable) onPlay(item.id, false) else vm.openDetail(item)
                    }) {
                        Poster(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f), false)
                        Spacer(Modifier.height(4.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = item.seriesName ?: item.type
                        Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentView(parent: JellyItem, items: List<JellyItem>, loading: Boolean, vm: JellyfinViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(parent.name) { vm.closeParent() }
        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Column(Modifier.clickable { vm.openDetail(item) }) {
                        Poster(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f), false)
                        Spacer(Modifier.height(4.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsView(vm: JellyfinViewModel, downloads: JellyDownloadsState) {
    val items = remember(downloads.downloadedIds) { vm.downloadedItems() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Downloads") { vm.closeDownloads() }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Noch keine Downloads.\nTippe auf einer Detailseite auf 'Herunterladen'.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Column(Modifier.clickable { vm.openDetail(item) }) {
                        Poster(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f), false)
                        Spacer(Modifier.height(4.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailView(
    item: JellyItem,
    state: JellyfinUiState,
    downloads: JellyDownloadsState,
    vm: JellyfinViewModel,
    onPlay: (String, Boolean) -> Unit
) {
    var backdrop by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        backdrop = ServiceLocator.jellyfin.backdropUrl(item.id, item.backdropImageTags?.firstOrNull())
            ?: ServiceLocator.jellyfin.imageUrl(item.id, item.primaryTag, 1000)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            if (backdrop != null) {
                AsyncImage(model = backdrop, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.3f),
                        0.5f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
            )
            IconButton(onClick = { vm.closeDetail() }, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.productionYear?.let { Text(it.toString(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
                    item.runtimeMinutes?.let { Text("$it Min.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (item.isPlayable) {
                val resumeMs = ServiceLocator.jellyfin.ticksToMs(item.userData?.playbackPositionTicks ?: 0L)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    WhitePlayButton(if (resumeMs > 0) "Fortsetzen" else "Abspielen") { onPlay(item.id, false) }
                    if (resumeMs > 0) RoundIcon(Icons.Default.Replay, "Von vorne") { onPlay(item.id, true) }
                    val fav = item.userData?.isFavorite == true
                    RoundIcon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorit",
                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) { vm.toggleFavorite(item) }
                    DownloadControl(item, downloads, vm)
                }
            }
            item.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.type == "Series") {
                // Staffel-Auswahl
                if (state.seasons.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.seasons, key = { it.id }) { season ->
                            val selected = state.selectedSeason?.id == season.id
                            Text(
                                season.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { vm.selectSeason(season) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                if (state.loading && state.episodes.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.episodes.forEach { ep ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlay(ep.id, false) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Poster(ep, Modifier.width(120.dp).aspectRatio(16f / 9f), true)
                        Column(Modifier.weight(1f)) {
                            val prefix = ep.indexNumber?.let { "F$it" } ?: ""
                            Text(if (prefix.isNotBlank()) "$prefix · ${ep.name}" else ep.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ep.runtimeMinutes?.let { Text("$it Min.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        val dl = ep.id in downloads.downloadedIds
                        IconButton(onClick = { if (dl) vm.deleteDownload(ep.id) else vm.download(ep) }) {
                            Icon(if (dl) Icons.Default.DownloadDone else Icons.Default.Download, "Download", tint = if (dl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DownloadControl(item: JellyItem, downloads: JellyDownloadsState, vm: JellyfinViewModel) {
    val downloaded = item.id in downloads.downloadedIds
    val pct = downloads.progress[item.id]
    when {
        pct != null -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { pct }, modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
        }
        downloaded -> RoundIcon(Icons.Default.DownloadDone, "Heruntergeladen", tint = MaterialTheme.colorScheme.primary) { vm.deleteDownload(item.id) }
        else -> RoundIcon(Icons.Default.Download, "Herunterladen") { vm.download(item) }
    }
}

@Composable
private fun WhitePlayButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Poster(item: JellyItem, modifier: Modifier, landscape: Boolean) {
    var url by remember(item.id, landscape) {
        mutableStateOf<String?>(ServiceLocator.jellyfinDownloads.localPosterUri(item.id))
    }
    LaunchedEffect(item.id, landscape) {
        if (url == null) {
            url = if (landscape) {
                ServiceLocator.jellyfin.backdropUrl(item.id, item.backdropImageTags?.firstOrNull(), 500)
                    ?: ServiceLocator.jellyfin.imageUrl(item.id, item.primaryTag)
            } else {
                ServiceLocator.jellyfin.imageUrl(item.id, item.primaryTag)
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
