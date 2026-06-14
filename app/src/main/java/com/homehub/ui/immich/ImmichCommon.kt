package com.homehub.ui.immich

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Asset

/** Coil-Loader, der den Immich-API-Key bei jedem Bild-Request mitsendet. */
@Composable
fun rememberImmichImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .okHttpClient {
                com.homehub.data.network.Http.client.newBuilder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("x-api-key", ServiceLocator.immich.apiKey())
                                .build()
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

/** Einzelnes Thumbnail mit Video-/Favoriten-Kennzeichnung. */
@Composable
fun AssetThumb(
    asset: Asset,
    loader: ImageLoader,
    onClick: (Asset) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(asset.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset.id) { url = ServiceLocator.immich.thumbnailUrl(asset.id) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick(asset) }
    ) {
        url?.let {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(it).build(),
                imageLoader = loader,
                contentDescription = asset.originalFileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (asset.isVideo) {
            Icon(
                Icons.Default.PlayCircle, contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        if (asset.isFavorite) {
            Icon(
                Icons.Default.Favorite, contentDescription = "Favorit",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(14.dp)
            )
        }
    }
}

/** Wiederverwendbares Foto-Raster. */
@Composable
fun AssetGrid(
    assets: List<Asset>,
    onClick: (Asset) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(2.dp)
) {
    val loader = rememberImmichImageLoader()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        items(assets, key = { it.id }) { asset ->
            AssetThumb(asset, loader, onClick, Modifier.padding(2.dp))
        }
    }
}

/**
 * Thumbnail eines LOKALEN Mediums, das noch nicht auf dem Server liegt.
 * Wird mit einem Cloud-Upload-Badge markiert.
 */
@Composable
fun LocalThumb(
    item: com.homehub.data.local.LocalItem,
    onClick: (com.homehub.data.local.LocalItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick(item) }
    ) {
        AsyncImage(
            model = item.uri, // Coil lädt content:// direkt
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.isVideo) {
            Icon(
                Icons.Default.PlayCircle, contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        // Badge: noch nicht gesichert
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(3.dp)
        ) {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = "Noch nicht hochgeladen",
                tint = Color(0xFFF6AD55),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
