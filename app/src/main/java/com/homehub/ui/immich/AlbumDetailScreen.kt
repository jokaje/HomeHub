package com.homehub.ui.immich

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.homehub.data.immich.Asset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    vm: ImmichViewModel,
    onOpenAsset: (Asset) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    // Lädt das Album bei Änderungen (z.B. Bild gelöscht/archiviert) automatisch neu
    LaunchedEffect(albumId, state.revision) { vm.openAlbum(albumId) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.albumDetail?.albumName ?: "Album") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                }
            },
            actions = {
                IconButton(onClick = {
                    vm.shareAlbum(albumId) { url ->
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }, "Album-Link teilen"))
                    }
                }) { Icon(Icons.Default.Share, "Album teilen") }
            }
        )
        val album = state.albumDetail
        when {
            album == null -> Loading()
            album.assets.isEmpty() -> EmptyHint("Dieses Album ist leer.")
            else -> AssetGrid(
                album.assets,
                { vm.viewerList = album.assets; onOpenAsset(it) },
                Modifier.fillMaxSize()
            )
        }
    }
}
