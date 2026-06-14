package com.homehub.ui.immich

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Asset
import com.homehub.data.immich.Person
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ---------- Alben ----------
@Composable
internal fun AlbumsTab(state: ImmichUiState, vm: ImmichViewModel, onOpenAlbum: (String) -> Unit) {
    val loader = rememberImmichImageLoader()
    // Lädt initial und nach jeder Änderung (state.revision) automatisch neu
    LaunchedEffect(state.revision) { vm.loadAlbums() }

    if (state.albums.isEmpty()) {
        EmptyHint("Keine Alben gefunden."); return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.albums, key = { it.id }) { album ->
            Column(Modifier.clickable { onOpenAlbum(album.id) }) {
                var thumb by remember(album.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(album.id) {
                    album.albumThumbnailAssetId?.let {
                        thumb = ServiceLocator.immich.thumbnailUrl(it, size = "preview")
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    thumb?.let {
                        AsyncImage(
                            model = it, imageLoader = loader, contentDescription = album.albumName,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    album.albumName, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "${album.assetCount} Elemente" + if (album.shared) " · geteilt" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- Suche (Smart Search / CLIP) ----------
@Composable
internal fun SearchTab(state: ImmichUiState, vm: ImmichViewModel, onOpenAsset: (Asset) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("z.B. \"Sonnenuntergang am Strand\"") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                TextButton(onClick = { vm.smartSearch(query) }, enabled = query.isNotBlank()) {
                    Text("Suchen")
                }
            },
            singleLine = true
        )
        when {
            state.loading -> Loading()
            state.searchResults.isEmpty() -> EmptyHint("Beschreibe in eigenen Worten, was du suchst – Immich findet es per KI-Suche.")
            else -> AssetGrid(state.searchResults, { vm.viewerList = state.searchResults; onOpenAsset(it) }, Modifier.fillMaxSize())
        }
    }
}

// ---------- Personen ----------
@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PeopleTab(state: ImmichUiState, vm: ImmichViewModel, onOpenAsset: (Asset) -> Unit) {
    val loader = rememberImmichImageLoader()
    var selected by remember { mutableStateOf<Person?>(null) }
    var renameTarget by remember { mutableStateOf<Person?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Tippen zeigt Fotos · langes Drücken benennt um",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, top = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            lazyListItems(state.people, key = { it.id }) { person ->
                var url by remember(person.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(person.id) { url = ServiceLocator.immich.personThumbnailUrl(person.id) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            selected = person
                            vm.loadPersonAssets(person.id)
                        },
                        onLongClick = { renameTarget = person }
                    )
                ) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        url?.let {
                            AsyncImage(
                                model = it, imageLoader = loader, contentDescription = person.name,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text(
                        person.name.ifBlank { "Unbenannt" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }
        when {
            state.people.isEmpty() -> EmptyHint("Keine Personen gefunden. Die Gesichtserkennung läuft serverseitig in Immich.")
            selected == null -> EmptyHint("Tippe auf eine Person, um ihre Fotos zu sehen.")
            state.loading -> Loading()
            else -> AssetGrid(state.libraryAssets, { vm.viewerList = state.libraryAssets; onOpenAsset(it) }, Modifier.fillMaxSize())
        }
    }

    // Umbenennen-Dialog
    renameTarget?.let { person ->
        var name by remember(person.id) { mutableStateOf(person.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Person umbenennen") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        vm.renamePerson(person.id, name.trim())
                        renameTarget = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("Speichern") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { renameTarget = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

// ---------- Bibliothek: Favoriten / Archiv / Papierkorb / Gesperrt ----------
@Composable
internal fun LibraryTab(state: ImmichUiState, vm: ImmichViewModel, onOpenAsset: (Asset) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity

    fun authenticate() {
        val act = activity ?: run { authError = "Authentifizierung nicht verfügbar."; return }
        val prompt = androidx.biometric.BiometricPrompt(
            act,
            androidx.core.content.ContextCompat.getMainExecutor(act),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    vm.loadLocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) authError = errString.toString()
                    mode = 0
                }
            }
        )
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Gesperrter Ordner")
            .setSubtitle("Entsperre den Ordner mit Fingerabdruck oder Geräte-PIN")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(mode, state.revision) {
        when (mode) {
            0 -> vm.loadFavorites()
            1 -> vm.loadArchive()
            2 -> vm.loadTrash()
            3 -> if (unlocked) vm.loadLocked() else authenticate()
        }
    }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterChip(selected = mode == 0, onClick = { mode = 0 },
                label = { Text("Favoriten") },
                leadingIcon = { Icon(Icons.Default.Favorite, null, Modifier.size(16.dp)) }) }
            item { FilterChip(selected = mode == 1, onClick = { mode = 1 },
                label = { Text("Archiv") },
                leadingIcon = { Icon(Icons.Default.Archive, null, Modifier.size(16.dp)) }) }
            item { FilterChip(selected = mode == 2, onClick = { mode = 2 },
                label = { Text("Papierkorb") },
                leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }) }
            item { FilterChip(selected = mode == 3, onClick = { mode = 3 },
                label = { Text("Gesperrt") },
                leadingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) }) }
        }
        authError?.let {
            Text(
                it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        if (mode == 2 && state.libraryAssets.isNotEmpty()) {
            Button(
                onClick = vm::emptyTrash,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) { Text("Papierkorb leeren") }
        }
        when {
            mode == 3 && !unlocked -> EmptyHint("Bitte zuerst entsperren.")
            state.loading -> Loading()
            state.libraryAssets.isEmpty() -> EmptyHint(
                if (mode == 3)
                    "Der gesperrte Ordner ist leer.\n\nÖffne ein Bild im Vollbild und tippe auf das Schloss-Symbol, um es hierher zu verschieben. Es verschwindet dann aus allen anderen Ansichten."
                else "Hier ist nichts."
            )
            else -> AssetGrid(state.libraryAssets, { vm.viewerList = state.libraryAssets; onOpenAsset(it) }, Modifier.fillMaxSize())
        }
    }
}

// ---------- Karte ----------
@Composable
internal fun ImmichMapTab(state: ImmichUiState) {
    val context = LocalContext.current
    if (state.mapMarkers.isEmpty()) {
        EmptyHint("Keine Fotos mit Standortdaten gefunden (oder die Karte lädt noch).")
        return
    }
    // clipToBounds verhindert, dass die Karten-View über die Tab-Leiste zeichnet
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                MapView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(5.0)
                    state.mapMarkers.firstOrNull()?.let {
                        controller.setCenter(GeoPoint(it.lat, it.lon))
                    }
                    onResume()
                }
            },
            update = { map ->
                map.overlays.clear()
                state.mapMarkers.take(800).forEach { m ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(m.lat, m.lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = listOfNotNull(m.city, m.country).joinToString(", ")
                    })
                }
                map.invalidate()
            },
            onRelease = { map ->
                // Beim Verlassen des Tabs sauber abbauen, sonst blockiert die
                // Karten-View Eingaben anderer Bereiche
                map.onPause()
                map.onDetach()
            }
        )
    }
}


@Composable
internal fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
