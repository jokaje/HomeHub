package com.homehub.ui.immich

import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Asset
import kotlinx.coroutines.launch

/**
 * Vollbild-Viewer:
 * - Wischen links/rechts blättert durch die Liste, aus der geöffnet wurde
 * - Runterwischen verkleinert das Bild und schließt den Viewer
 * - Hochwischen (oder Info-Button) öffnet die Detail-Ansicht
 * - Pinch-Zoom pro Bild; Videos streamen über ExoPlayer
 */
@OptIn(UnstableApi::class)
@Composable
fun AssetViewerScreen(
    assetId: String,
    vm: ImmichViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Durchblätterbare Liste: der Kontext, aus dem das Bild geöffnet wurde
    val assets = remember {
        mutableStateListOf<Asset>().apply {
            val ctxList = vm.viewerList.takeIf { l -> l.any { it.id == assetId } }
            addAll(ctxList ?: listOfNotNull(vm.findAsset(assetId)))
        }
    }
    LaunchedEffect(assetId) {
        if (assets.isEmpty()) {
            runCatching { ServiceLocator.immich.api.asset(assetId) }
                .onSuccess { assets.add(it) }
        }
    }
    if (assets.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Lade …", color = Color.White)
        }
        return
    }

    val startIndex = assets.indexOfFirst { it.id == assetId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex) { assets.size }
    val current = assets[pagerState.currentPage.coerceIn(0, assets.lastIndex)]

    var showInfo by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var pageZoomed by remember { mutableStateOf(false) }

    fun replace(updated: Asset) {
        val i = assets.indexOfFirst { it.id == updated.id }
        if (i >= 0) assets[i] = updated
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                // Runterwischen: Bild folgt dem Finger und schrumpft sanft
                val y = dragY.coerceAtLeast(0f)
                translationY = y
                val s = (1f - y / 2200f).coerceIn(0.75f, 1f)
                scaleX = s; scaleY = s
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dy ->
                        if (!pageZoomed) {
                            dragY += dy
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        when {
                            dragY > 220f -> onBack()
                            dragY < -140f -> { showInfo = true; dragY = 0f }
                            else -> dragY = 0f
                        }
                    },
                    onDragCancel = { dragY = 0f }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val asset = assets[page]
            if (asset.isVideo) {
                VideoPage(asset, isActive = page == pagerState.currentPage)
            } else {
                ImagePage(asset) { zoomed ->
                    if (page == pagerState.currentPage) pageZoomed = zoomed
                }
            }
        }

        // Kopfzeile
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    current.originalFileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White, maxLines = 1
                )
                Text(
                    "${pagerState.currentPage + 1} / ${assets.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Outlined.Info, "Details anzeigen", tint = Color.White)
            }
        }

        // Aktionsleiste
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { vm.toggleFavorite(current) { replace(it) } }) {
                Icon(
                    if (current.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorit", tint = if (current.isFavorite) Color(0xFFE08070) else Color.White
                )
            }
            IconButton(onClick = { vm.toggleArchive(current) { replace(it) } }) {
                Icon(
                    if (current.archivedEffective) Icons.Default.Unarchive else Icons.Default.Archive,
                    "Archiv", tint = Color.White
                )
            }
            // Gesperrter Ordner (HomeHub: Archiv + #locked-Tag)
            IconButton(onClick = {
                if (current.lockedCustom) vm.unlockAsset(current) { replace(it) }
                else { vm.lockAsset(current) { replace(it) }; onBack() }
            }) {
                Icon(
                    if (current.lockedCustom) Icons.Default.LockOpen else Icons.Default.Lock,
                    if (current.lockedCustom) "Entsperren" else "In gesperrten Ordner",
                    tint = if (current.lockedCustom) Color(0xFFF2B27E) else Color.White
                )
            }
            IconButton(onClick = {
                if (sharing) return@IconButton
                sharing = true
                scope.launch {
                    val result = ServiceLocator.immich.downloadOriginal(context, current)
                    sharing = false
                    result.onSuccess { file ->
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val mime = if (current.isVideo) "video/*" else "image/*"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Teilen"))
                    }
                }
            }) {
                if (sharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(10.dp),
                        strokeWidth = 2.dp, color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Share, "Teilen", tint = Color.White)
                }
            }
            if (current.isTrashed) {
                IconButton(onClick = { vm.restoreAsset(current); onBack() }) {
                    Icon(Icons.Default.RestoreFromTrash, "Wiederherstellen", tint = Color.White)
                }
            } else {
                IconButton(onClick = { vm.trashAsset(current) { }; onBack() }) {
                    Icon(Icons.Default.Delete, "Löschen", tint = Color.White)
                }
            }
        }

        if (showInfo) {
            AssetInfoSheet(asset = current, onDismiss = { showInfo = false })
        }
    }
}

/** Einzelne Bild-Seite mit Pinch-Zoom. Konsumiert Gesten nur bei
 *  Mehrfingereingabe oder aktivem Zoom – Wischen bleibt frei für den Pager. */
@Composable
private fun ImagePage(asset: Asset, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(asset.id) { mutableFloatStateOf(1f) }
    var offset by remember(asset.id) { mutableStateOf(Offset.Zero) }
    var url by remember(asset.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset.id) { url = ServiceLocator.immich.originalUrl(asset.id) }
    LaunchedEffect(scale) { onZoomChanged(scale > 1.02f) }

    val loader = rememberImmichImageLoader()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        url?.let {
            AsyncImage(
                model = it,
                imageLoader = loader,
                contentDescription = asset.originalFileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(asset.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val multiTouch = event.changes.size >= 2
                                if (multiTouch || scale > 1.02f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (scale > 1f) offset + pan else Offset.Zero
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y
                    )
            )
        }
    }
}

/** Video-Seite: spielt nur, wenn sie die aktuell sichtbare Seite ist. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPage(asset: Asset, isActive: Boolean) {
    var videoUrl by remember(asset.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset.id) { videoUrl = ServiceLocator.immich.videoPlaybackUrl(asset.id) }

    if (!isActive) {
        // Inaktive Seiten zeigen nur das Vorschaubild
        var thumb by remember(asset.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(asset.id) { thumb = ServiceLocator.immich.thumbnailUrl(asset.id, "preview") }
        val loader = rememberImmichImageLoader()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            thumb?.let {
                AsyncImage(
                    model = it, imageLoader = loader, contentDescription = asset.originalFileName,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    val context = LocalContext.current
    videoUrl?.let { url ->
        val player = remember(url) {
            val dataSourceFactory = OkHttpDataSource.Factory(ServiceLocator.immich.httpClient)
            ExoPlayer.Builder(context).build().apply {
                setMediaSource(
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(url))
                )
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AssetInfoSheet(asset: Asset, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Details", style = MaterialTheme.typography.titleLarge)

            val exif = asset.exifInfo

            exif?.description?.takeIf { it.isNotBlank() }?.let {
                InfoRow("Beschreibung", it)
            }
            InfoRow("Aufgenommen", formatDateTime(exif?.dateTimeOriginal ?: asset.fileCreatedAt))

            val place = listOfNotNull(exif?.city, exif?.state, exif?.country)
                .filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotBlank()) InfoRow("Aufnahmeort", place)
            if (exif?.latitude != null && exif.longitude != null) {
                InfoRow("Koordinaten", "%.5f, %.5f".format(exif.latitude, exif.longitude))
            }

            val camera = listOfNotNull(exif?.make, exif?.model).joinToString(" ")
            if (camera.isNotBlank()) InfoRow("Kamera", camera)
            exif?.lensModel?.takeIf { it.isNotBlank() }?.let { InfoRow("Objektiv", it) }
            val shot = buildList {
                exif?.fNumber?.let { add("ƒ/$it") }
                exif?.exposureTime?.let { add("${it}s") }
                exif?.iso?.let { add("ISO $it") }
                exif?.focalLength?.let { add("${it}mm") }
            }.joinToString("  ·  ")
            if (shot.isNotBlank()) InfoRow("Aufnahme", shot)

            InfoRow("Dateiname", asset.originalFileName)
            val dims = if (exif?.exifImageWidth != null && exif.exifImageHeight != null)
                "${exif.exifImageWidth} × ${exif.exifImageHeight}" else null
            dims?.let { InfoRow("Auflösung", it) }
            exif?.fileSizeInByte?.let { InfoRow("Größe", formatBytes(it)) }
            if (asset.isVideo) asset.duration?.let { InfoRow("Dauer", it.removePrefix("0:")) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatDateTime(iso: String): String = runCatching {
    val date = java.time.OffsetDateTime.parse(iso)
    date.format(
        java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy · HH:mm", java.util.Locale.GERMAN)
    )
}.getOrElse { iso }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
