package com.homehub.ui.immich

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Asset
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tabs = listOf("Fotos", "Alben", "Suche", "Personen", "Bibliothek", "Karte")

@Composable
fun ImmichHomeScreen(
    vm: ImmichViewModel,
    onOpenAsset: (Asset) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error, state.info) {
        (state.error ?: state.info)?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    // Medien-Berechtigung: ohne sie kann die App lokale (noch nicht gesicherte)
    // Fotos weder anzeigen noch hochladen.
    fun hasMediaPermission(): Boolean = mediaPermissionList().any {
        androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var mediaGranted by remember { mutableStateOf(hasMediaPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        mediaGranted = result.values.any { it }
        if (mediaGranted) vm.scanLocalPending()
    }

    // Lokale Medien scannen (die Funktion meldet selbst, falls die Berechtigung fehlt)
    LaunchedEffect(state.configured, mediaGranted) {
        if (state.configured) vm.scanLocalPending()
    }

    // Beim ersten Mal automatisch nach Medienzugriff fragen
    var askedForMedia by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!mediaGranted && !askedForMedia) {
            askedForMedia = true
            permissionLauncher.launch(mediaPermissionList())
        }
    }

    // Beim Zurueckkehren in die App: Berechtigung neu pruefen + neue Aufnahmen erfassen
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                mediaGranted = hasMediaPermission()
                vm.scanLocalPending()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Manueller Upload über den System-Fotopicker (mit SHA-Duplikatprüfung)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var ok = 0
            var skipped = 0
            uris.forEach { uri ->
                // Duplikatprüfung per SHA-1 + bulk-upload-check
                val sha = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.homehub.data.local.LocalMedia.sha1(context.contentResolver, uri)
                }
                val isDuplicate = sha != null && runCatching {
                    ServiceLocator.immich.api.bulkUploadCheck(
                        com.homehub.data.immich.UploadCheckRequest(
                            listOf(com.homehub.data.immich.UploadCheckItem("manual", sha))
                        )
                    ).results.firstOrNull()?.action.equals("reject", true)
                }.getOrDefault(false)

                if (isDuplicate) { skipped++; return@forEach }

                val name = "homehub_${System.currentTimeMillis()}"
                val result = ServiceLocator.immich.uploadFromUri(
                    context.contentResolver, uri, name,
                    createdAt = System.currentTimeMillis(),
                    deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "homehub"
                )
                if (result.isSuccess) ok++
            }
            val skippedText = if (skipped > 0) " · $skipped bereits vorhanden" else ""
            snackbar.showSnackbar("$ok von ${uris.size} Dateien hochgeladen$skippedText")
            vm.refreshTimeline()
            vm.scanLocalPending()
        }
    }

    if (!state.configured) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Immich ist noch nicht eingerichtet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Hinterlege URL und API-Key unter Einstellungen → Immich.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 8.dp,
            // zIndex hält die Tab-Leiste sichtbar über der Karten-View
            modifier = Modifier.zIndex(2f)
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = {
                    tab = i
                    when (i) {
                        1 -> vm.loadAlbums()
                        3 -> vm.loadPeople()
                        5 -> vm.loadMap()
                    }
                }, text = { Text(title) })
            }
        }

        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Column(Modifier.fillMaxSize()) {
                    state.localScanStatus?.let { status ->
                        Text(
                            "Lokal: $status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    if (!mediaGranted) {
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Um noch nicht gesicherte Fotos anzuzeigen, braucht HomeHub Zugriff auf deine Medien.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                androidx.compose.material3.TextButton(
                                    onClick = { permissionLauncher.launch(mediaPermissionList()) }
                                ) { Text("Erlauben") }
                            }
                        }
                    }
                    TimelineTab(state, vm, onOpenAsset)
                }
                1 -> AlbumsTab(state, vm, onOpenAlbum)
                2 -> SearchTab(state, vm, onOpenAsset)
                3 -> PeopleTab(state, vm, onOpenAsset)
                4 -> LibraryTab(state, vm, onOpenAsset)
                5 -> ImmichMapTab(state)
            }
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    text = { Text("Hochladen") }
                )
            }
        }
        SnackbarHost(snackbar)
    }
}

// ---------- Tab: Fotos (Timeline, Monat/Tag per Pinch-Zoom, inkl. lokaler Medien) ----------

/** Eintrag in der Timeline: entweder Server-Asset oder lokales, noch nicht gesichertes Medium. */
private sealed interface TimelineEntry {
    val sortKey: String
    data class Remote(val asset: Asset) : TimelineEntry {
        override val sortKey get() = asset.fileCreatedAt
    }
    data class Local(val item: com.homehub.data.local.LocalItem) : TimelineEntry {
        override val sortKey get() = isoFromMillis(item.createdAtMs)
    }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTab(state: ImmichUiState, vm: ImmichViewModel, onOpenAsset: (Asset) -> Unit) {
    val loader = rememberImmichImageLoader()
    if (state.loading && state.buckets.isEmpty()) {
        Loading(); return
    }

    // Granularität: MONTH (4 Spalten) oder DAY (3 Spalten) – Auswahl bleibt gespeichert.
    // Pinch-Zoom: auseinanderziehen -> Tage, zusammenziehen -> Monate.
    var granularity by remember {
        mutableStateOf(com.homehub.core.ServiceLocator.settings.timelineGranularity)
    }
    fun setGranularity(g: String) {
        granularity = g
        com.homehub.core.ServiceLocator.settings.timelineGranularity = g
    }
    var zoomAccumulator by remember { mutableFloatStateOf(1f) }
    var pendingDialog by remember { mutableStateOf<com.homehub.data.local.LocalItem?>(null) }

    val columns = if (granularity == "DAY") 3 else 4

    data class Section(val key: String, val title: String, val count: Int, val entries: List<TimelineEntry>?, val loading: Boolean)

    // Lokale Pending-Medien nach Gruppenschlüssel sortieren
    val localByMonth = state.localPending.groupBy { monthKey(it.createdAtMs) }   // yyyy-MM
    val localByDay = state.localPending.groupBy { dayKey(it.createdAtMs) }       // yyyy-MM-dd

    val sections: List<Section> = if (granularity == "MONTH") {
        val serverSections = state.buckets.map { b ->
            val mKey = b.bucket.timeBucket.take(7)
            val locals = localByMonth[mKey].orEmpty().map { TimelineEntry.Local(it) }
            val remotes = b.assets?.map { TimelineEntry.Remote(it) }
            Section(
                key = b.bucket.timeBucket,
                title = formatBucket(b.bucket.timeBucket),
                count = b.bucket.count + locals.size,
                entries = if (remotes == null && locals.isEmpty()) null
                else ((locals + remotes.orEmpty()).sortedByDescending { it.sortKey }),
                loading = b.loading
            )
        }
        // Monate, die NUR lokale Medien enthalten, zusätzlich einfügen
        val serverMonths = state.buckets.map { it.bucket.timeBucket.take(7) }.toSet()
        val localOnly = localByMonth.filterKeys { it !in serverMonths }.map { (mKey, items) ->
            Section(
                key = "$mKey-01T00:00:00.000Z",
                title = formatBucket("$mKey-01T00:00:00.000Z"),
                count = items.size,
                entries = items.sortedByDescending { it.createdAtMs }.map { TimelineEntry.Local(it) },
                loading = false
            )
        }
        (serverSections + localOnly).sortedByDescending { it.key }
    } else {
        val remoteEntries = state.buckets.flatMap { it.assets.orEmpty() }
            .map { TimelineEntry.Remote(it) as TimelineEntry }
        val localEntries = state.localPending.map { TimelineEntry.Local(it) as TimelineEntry }
        (remoteEntries + localEntries)
            .sortedByDescending { it.sortKey }
            .groupBy { it.sortKey.take(10) } // yyyy-MM-dd
            .map { (day, list) ->
                Section(key = "day-$day", title = formatDay(day), count = list.size, entries = list, loading = false)
            }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }
    // Nach einem Pull-to-Refresh: Spinner zuverlaessig beenden (mit Timeout, damit
    // er nie haengen bleibt) und danach robust an den Listenanfang scrollen.
    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        kotlinx.coroutines.delay(350) // refreshTimeline Zeit geben, loading=true zu setzen
        val start = System.currentTimeMillis()
        while (state.loading && System.currentTimeMillis() - start < 8000) {
            kotlinx.coroutines.delay(100)
        }
        // Mehrfach nach oben verankern, gegen die key-basierte Positionswiederherstellung
        repeat(5) {
            listState.scrollToItem(0)
            kotlinx.coroutines.delay(120)
        }
        refreshing = false
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            vm.refreshTimeline()
            vm.scanLocalPending(report = true)
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Nur Zwei-Finger-Gesten abfangen, normales Scrollen bleibt frei
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            zoomAccumulator *= event.calculateZoom()
                            if (zoomAccumulator > 1.3f) { setGranularity("DAY"); zoomAccumulator = 1f }
                            if (zoomAccumulator < 0.75f) { setGranularity("MONTH"); zoomAccumulator = 1f }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    zoomAccumulator = 1f
                }
            }
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 80.dp)) {
            sections.forEach { section ->
                item(key = "header-${section.key}") {
                    if (granularity == "MONTH" && !section.key.startsWith("day-")) {
                        LaunchedEffect(section.key) {
                            if (!section.key.contains("local")) vm.loadBucket(section.key)
                        }
                    }
                    Text(
                        section.title + "  ·  " + section.count,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                val entries = section.entries
                if (entries != null) {
                    val rows = entries.chunked(columns)
                    items(rows.size, key = { "row-${section.key}-$it-$granularity" }) { rowIndex ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                            rows[rowIndex].forEach { entry ->
                                when (entry) {
                                    is TimelineEntry.Remote -> AssetThumb(
                                        entry.asset, loader,
                                        onClick = { clicked ->
                                            // Viewer-Kontext: alle Server-Assets der Timeline in Reihenfolge
                                            vm.viewerList = sections
                                                .flatMap { it.entries.orEmpty() }
                                                .filterIsInstance<TimelineEntry.Remote>()
                                                .map { it.asset }
                                            onOpenAsset(clicked)
                                        },
                                        Modifier.weight(1f).padding(2.dp)
                                    )
                                    is TimelineEntry.Local -> LocalThumb(
                                        entry.item,
                                        onClick = { pendingDialog = it },
                                        Modifier.weight(1f).padding(2.dp)
                                    )
                                }
                            }
                            repeat(columns - rows[rowIndex].size) {
                                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else if (section.loading) {
                    item { Loading(small = true) }
                }
            }
            if (vm.hasPendingTimeline || vm.hasMoreTimeline) {
                item(key = "load-more") {
                    if (granularity == "DAY") {
                        LaunchedEffect(sections.size) { vm.loadNextPending() }
                        Loading(small = true)
                    } else if (vm.hasMoreTimeline) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { vm.loadTimelinePage() },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) { Text(if (state.loading) "Lädt …" else "Mehr laden") }
                    }
                }
            }
        }

        // Anzeige der aktuellen Ansicht
        androidx.compose.material3.Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ) {
            Text(
                if (granularity == "DAY") "Tage" else "Monate",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }

    // Dialog für noch nicht hochgeladene Medien
    pendingDialog?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDialog = null },
            title = { Text("Noch nicht gesichert") },
            text = {
                Text(
                    "'${item.name}' liegt nur auf diesem Gerät und wird beim nächsten " +
                        "automatischen Backup gesichert – oder du lädst es jetzt direkt hoch."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.uploadLocal(item)
                    pendingDialog = null
                }) { Text("Jetzt hochladen") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDialog = null }) {
                    Text("Schließen")
                }
            }
        )
    }
}

@Composable
internal fun Loading(small: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) { CircularProgressIndicator(modifier = Modifier.padding(8.dp)) }
}

private fun formatBucket(iso: String): String = runCatching {
    val date = OffsetDateTime.parse(iso)
    date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
}.getOrElse { iso.take(7) }

private fun formatDay(day: String): String = runCatching {
    val date = java.time.LocalDate.parse(day)
    date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
}.getOrElse { day }


private fun mediaPermissionList(): Array<String> =
    if (android.os.Build.VERSION.SDK_INT >= 34) arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) else if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    ) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)

private fun isoFromMillis(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).toString() // ISO-8601 UTC, sortiert korrekt

private fun monthKey(ms: Long): String = isoFromMillis(ms).take(7)
private fun dayKey(ms: Long): String = isoFromMillis(ms).take(10)
