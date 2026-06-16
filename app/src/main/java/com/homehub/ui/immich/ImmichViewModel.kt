package com.homehub.ui.immich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehub.core.ServiceLocator
import com.homehub.data.immich.Album
import com.homehub.data.immich.Asset
import com.homehub.data.immich.BulkIds
import com.homehub.data.immich.CreateSharedLinkRequest
import com.homehub.data.immich.ExifInfo
import com.homehub.data.immich.MapMarker
import com.homehub.data.immich.MetadataSearchRequest
import com.homehub.data.immich.Person
import com.homehub.data.immich.SmartSearchRequest
import com.homehub.data.immich.TimeBucket
import com.homehub.data.immich.UpdateAssetRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BucketSection(val bucket: TimeBucket, val assets: List<Asset>?, val loading: Boolean = false)

data class ImmichUiState(
    val configured: Boolean = false,
    val buckets: List<BucketSection> = emptyList(),
    val localPending: List<com.homehub.data.local.LocalItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val albumDetail: Album? = null,
    val people: List<Person> = emptyList(),
    val searchResults: List<Asset> = emptyList(),
    val libraryAssets: List<Asset> = emptyList(), // Favoriten/Archiv/Papierkorb/Person
    val mapMarkers: List<MapMarker> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val localScanStatus: String? = null,
    // Zähler für Mutationen: Ansichten laden sich bei Änderung automatisch neu
    val revision: Int = 0
)

class ImmichViewModel : ViewModel() {

    private val repo get() = ServiceLocator.immich
    private val _state = MutableStateFlow(ImmichUiState())
    val state: StateFlow<ImmichUiState> = _state

    /** Kontext für den Vollbild-Viewer: die Liste, aus der ein Bild geöffnet wurde
     *  (zum Durchblättern per Wischgeste). */
    var viewerList: List<Asset> = emptyList()

    /** Nach jeder Änderung hochzählen -> offene Ansichten aktualisieren sich selbst. */
    private fun bumpRevision() = _state.update { it.copy(revision = it.revision + 1) }

    /** Blendet HomeHub-gesperrte Assets (#locked) aus normalen Ansichten aus. */
    private fun List<Asset>.withoutLocked(): List<Asset> =
        filter { !it.lockedCustom && it.id !in lockedIds }

    /** IDs aller #locked-Assets. Die Timeline-API liefert keine Beschreibungen,
     *  daher scannen wir die IDs separat und filtern die Timeline dagegen. */
    private var lockedIds: Set<String> = emptySet()

    /** Entfernt ein Asset aus Timeline & Suche (z.B. nach Archivieren/Sperren). */
    private fun removeFromBrowse(id: String) = _state.update { s ->
        s.copy(
            buckets = s.buckets.map { b -> b.copy(assets = b.assets?.filterNot { it.id == id }) },
            searchResults = s.searchResults.filterNot { it.id == id }
        )
    }

    init { refreshTimeline() }

    /** Person umbenennen (Personen-Tab, langes Drücken auf ein Gesicht). */
    fun renamePerson(personId: String, name: String) = viewModelScope.launch {
        runCatching {
            repo.api.updatePerson(personId, com.homehub.data.immich.UpdatePersonRequest(name = name))
        }.onSuccess { updated ->
            _state.update { s ->
                s.copy(
                    people = s.people.map { if (it.id == personId) updated else it },
                    info = "Name gespeichert"
                )
            }
        }.onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun dismissMessage() = _state.update { it.copy(error = null, info = null) }

    // ---------- Timeline ----------
    // Primär: /api/timeline/buckets. Liefert der Server damit Fehler (ältere/neuere
    // API-Varianten), wird automatisch auf die Metadaten-Suche umgeschaltet und
    // clientseitig nach Monaten gruppiert.
    private var fallbackMode = false
    private var fallbackPage = 1
    private var fallbackHasMore = true

    fun refreshTimeline() = viewModelScope.launch {
        if (!ServiceLocator.settings.get(com.homehub.data.settings.ServiceId.IMMICH).isConfigured) {
            _state.update { it.copy(configured = false) }; return@launch
        }
        _state.update { it.copy(configured = true, loading = true) }
        fallbackMode = false; fallbackPage = 1; fallbackHasMore = true
        scanLockedIds() // #locked-IDs im Hintergrund ermitteln
        scanLocalPending() // lokale, noch nicht gesicherte Medien einblenden

        runCatching { repo.api.timelineBuckets() }
            .onSuccess { buckets ->
                _state.update { s -> s.copy(loading = false, buckets = buckets.map { BucketSection(it, null) }) }
                buckets.firstOrNull()?.let { loadBucket(it.timeBucket) }
            }
            .onFailure {
                // Timeline-API passt nicht zur Server-Version -> Such-Fallback
                fallbackMode = true
                loadTimelinePage(reset = true)
            }
    }

    /**
     * Ermittelt alle #locked-Asset-IDs (per Beschreibung) und filtert die
     * bereits geladene Timeline dagegen. Funktioniert unabhängig davon, ob das
     * Archivieren auf dem Server klappt – allein der #locked-Tag zählt.
     */
    private fun scanLockedIds() = viewModelScope.launch {
        val ids = runCatching {
            repo.api.metadataSearch(MetadataSearchRequest(withArchived = true, size = 1000))
        }.getOrNull()?.assets?.items?.filter { it.lockedCustom }?.map { it.id }?.toSet().orEmpty()
        if (ids != lockedIds) {
            lockedIds = ids
            _state.update { s ->
                s.copy(buckets = s.buckets.map { b -> b.copy(assets = b.assets?.filter { it.id !in ids }) })
            }
        }
    }

    fun loadBucket(timeBucket: String) = viewModelScope.launch {
        if (fallbackMode) return@launch
        val current = _state.value.buckets
        val idx = current.indexOfFirst { it.bucket.timeBucket == timeBucket }
        if (idx < 0 || current[idx].assets != null || current[idx].loading) return@launch
        _state.update { s ->
            s.copy(buckets = s.buckets.mapIndexed { i, b -> if (i == idx) b.copy(loading = true) else b })
        }
        runCatching { repo.api.timelineBucket(timeBucket) }
            .onSuccess { raw ->
                val assets = raw.withoutLocked()
                _state.update { s ->
                    s.copy(buckets = s.buckets.map {
                        if (it.bucket.timeBucket == timeBucket) it.copy(assets = assets, loading = false) else it
                    })
                }
            }
            .onFailure {
                // Auch der Bucket-Endpunkt passt nicht -> komplett auf Fallback umschalten
                fallbackMode = true
                loadTimelinePage(reset = true)
            }
    }

    val isFallbackTimeline: Boolean get() = fallbackMode
    val hasMoreTimeline: Boolean get() = fallbackMode && fallbackHasMore

    /** Lädt die nächste Seite der Fallback-Timeline (200 Assets) und gruppiert nach Monat. */
    fun loadTimelinePage(reset: Boolean = false) = viewModelScope.launch {
        if (!fallbackMode) return@launch
        if (reset) { fallbackPage = 1; fallbackHasMore = true }
        if (!fallbackHasMore) return@launch
        _state.update { it.copy(loading = true) }
        runCatching {
            repo.api.metadataSearch(MetadataSearchRequest(page = fallbackPage, size = 200))
        }
            .onSuccess { r ->
                val newAssets = r.assets.items.filter { !it.isTrashed && !it.archivedEffective && !it.lockedCustom }
                fallbackHasMore = r.assets.nextPage != null && r.assets.items.isNotEmpty()
                fallbackPage++
                _state.update { s ->
                    val existing = if (reset) emptyList() else s.buckets.flatMap { it.assets.orEmpty() }
                    val all = (existing + newAssets).distinctBy { it.id }
                    s.copy(loading = false, buckets = groupByMonth(all))
                }
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
    }

    private fun groupByMonth(assets: List<Asset>): List<BucketSection> =
        assets.sortedByDescending { it.fileCreatedAt }
            .groupBy { it.fileCreatedAt.take(7) } // "yyyy-MM"
            .map { (month, list) ->
                BucketSection(
                    bucket = TimeBucket(timeBucket = "$month-01T00:00:00.000Z", count = list.size),
                    assets = list
                )
            }

    // ---------- Alben ----------
    fun loadAlbums() = viewModelScope.launch {
        runCatching { repo.api.albums() }
            .onSuccess { a -> _state.update { it.copy(albums = a) } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun openAlbum(id: String) = viewModelScope.launch {
        _state.update { it.copy(albumDetail = null, loading = true) }
        runCatching { repo.api.album(id) }
            .onSuccess { a -> _state.update { it.copy(albumDetail = a.copy(assets = a.assets.withoutLocked()), loading = false) } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e), loading = false) } }
    }

    // ---------- Suche ----------
    fun smartSearch(query: String) = viewModelScope.launch {
        if (query.isBlank()) return@launch
        _state.update { it.copy(loading = true, searchResults = emptyList()) }
        runCatching { repo.api.smartSearch(SmartSearchRequest(query = query)) }
            .onSuccess { r -> _state.update { it.copy(searchResults = r.assets.items.withoutLocked(), loading = false) } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e), loading = false) } }
    }

    // ---------- Personen ----------
    fun loadPeople() = viewModelScope.launch {
        runCatching { repo.api.people() }
            .onSuccess { r -> _state.update { it.copy(people = r.people.filter { p -> !p.isHidden }) } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun loadPersonAssets(personId: String) = loadLibrary(MetadataSearchRequest(personIds = listOf(personId)), { !it.lockedCustom })

    // ---------- Bibliothek (Favoriten / Archiv / Papierkorb / Gesperrt) ----------
    // Favoriten: withArchived=true, damit auch archivierte Favoriten erscheinen;
    // gesperrte (#locked) werden ausgeblendet.
    fun loadFavorites() = loadLibrary(
        MetadataSearchRequest(isFavorite = true, withArchived = true),
        { it.isFavorite && !it.lockedCustom }
    )

    // Archiv: ENTSCHEIDEND ist withArchived=true – sonst liefert die Suche keine
    // archivierten Assets. visibility="archive" filtert serverseitig zusätzlich.
    fun loadArchive() = loadLibrary(
        MetadataSearchRequest(withArchived = true, visibility = "archive"),
        { it.archivedEffective && !it.lockedCustom },
        fallback = { fetchViaTimeline(visibility = "archive").filter { !it.lockedCustom } }
    )

    fun loadTrash() = loadLibrary(
        MetadataSearchRequest(isTrashed = true, withDeleted = true),
        { it.isTrashed },
        fallback = { fetchViaTimeline(isTrashed = true) }
    )

    /**
     * HomeHub-eigener gesperrter Ordner: alle Assets, deren Beschreibung
     * "#locked" enthält – unabhängig vom Archiv-Status. So funktioniert der
     * Tresor auch dann, wenn das Archivieren auf dem Server nicht greift.
     */
    fun loadLocked() = viewModelScope.launch {
        _state.update { it.copy(loading = true, libraryAssets = emptyList()) }
        val items = runCatching {
            repo.api.metadataSearch(MetadataSearchRequest(withArchived = true, size = 1000))
        }.getOrNull()?.assets?.items.orEmpty()
        val locked = items.filter { it.lockedCustom }
        // IDs gleich für die Timeline-Filterung übernehmen
        lockedIds = locked.map { it.id }.toSet()
        _state.update { it.copy(libraryAssets = locked, loading = false) }
    }

    /** Verschiebt ein Asset in den gesperrten Ordner: Archiv + "#locked"-Tag. */
    fun lockAsset(asset: Asset, onUpdated: (Asset) -> Unit = {}) = viewModelScope.launch {
        val oldDesc = asset.effectiveDescription.orEmpty()
        val newDesc = if (oldDesc.contains("#locked", true)) oldDesc
        else (oldDesc.trim() + " #locked").trim()
        runCatching {
            repo.api.updateAsset(
                asset.id,
                UpdateAssetRequest(isArchived = true, visibility = "archive", description = newDesc)
            )
        }.onSuccess { updated ->
            // Beschreibung lokal in beiden Feldern sicherstellen
            val withDesc = updated.copy(
                description = newDesc,
                exifInfo = (updated.exifInfo ?: asset.exifInfo)?.copy(description = newDesc)
                    ?: ExifInfo(description = newDesc)
            )
            removeFromBrowse(asset.id)
            lockedIds = lockedIds + asset.id
            _state.update { s ->
                s.copy(
                    libraryAssets = s.libraryAssets.filterNot { it.id == asset.id },
                    info = "In den gesperrten Ordner verschoben"
                )
            }
            bumpRevision()
            onUpdated(withDesc)
        }.onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    /** Holt ein Asset aus dem gesperrten Ordner zurück (Tag entfernen + entarchivieren). */
    fun unlockAsset(asset: Asset, onUpdated: (Asset) -> Unit = {}) = viewModelScope.launch {
        val newDesc = asset.effectiveDescription.orEmpty()
            .replace(Regex("\\s*#locked", RegexOption.IGNORE_CASE), "").trim()
        runCatching {
            repo.api.updateAsset(
                asset.id,
                UpdateAssetRequest(isArchived = false, visibility = "timeline", description = newDesc)
            )
        }.onSuccess { updated ->
            lockedIds = lockedIds - asset.id
            _state.update { s ->
                s.copy(
                    libraryAssets = s.libraryAssets.filterNot { it.id == asset.id },
                    info = "Entsperrt – wieder in der Übersicht sichtbar"
                )
            }
            bumpRevision()
            refreshTimeline()
            onUpdated(updated)
        }.onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    /** Holt Assets über die Timeline-API mit Filter – Fallback für neue Server,
     *  deren Metadaten-Suche Archiv/Papierkorb/Gesperrt nicht (mehr) filtert. */
    private suspend fun fetchViaTimeline(
        visibility: String? = null,
        isTrashed: Boolean? = null
    ): List<Asset> = runCatching {
        val buckets = repo.api.timelineBuckets(visibility = visibility, isTrashed = isTrashed)
        buckets.take(24).flatMap { bucket ->
            runCatching {
                repo.api.timelineBucket(bucket.timeBucket, visibility = visibility, isTrashed = isTrashed)
            }.getOrDefault(emptyList())
        }
    }.getOrDefault(emptyList())

    private fun loadLibrary(
        req: MetadataSearchRequest,
        clientFilter: (Asset) -> Boolean = { true },
        fallback: (suspend () -> List<Asset>)? = null
    ) = viewModelScope.launch {
        _state.update { it.copy(loading = true, libraryAssets = emptyList()) }
        val fromSearch = runCatching { repo.api.metadataSearch(req) }
            .getOrNull()?.assets?.items?.filter(clientFilter).orEmpty()
        val result = if (fromSearch.isNotEmpty() || fallback == null) fromSearch
        else fallback()
        _state.update { it.copy(libraryAssets = result, loading = false) }
    }

    /**
     * Für die Tagesansicht: lädt den nächsten noch fehlenden Monat
     * (bzw. die nächste Fallback-Seite), wenn der Nutzer ans Listenende scrollt.
     */
    fun loadNextPending() {
        if (fallbackMode) { loadTimelinePage(); return }
        _state.value.buckets.firstOrNull { it.assets == null && !it.loading }
            ?.let { loadBucket(it.bucket.timeBucket) }
    }

    val hasPendingTimeline: Boolean
        get() = if (fallbackMode) fallbackHasMore
        else _state.value.buckets.any { it.assets == null }

    // ---------- Lokale, noch nicht hochgeladene Medien ----------
    /**
     * Durchsucht die in den Einstellungen gewählten Backup-Ordner nach Medien,
     * die noch NICHT auf dem Server liegen. Erkennung per SHA-1 +
     * Immich bulk-upload-check; Ergebnisse werden gecacht.
     */
    fun scanLocalPending(report: Boolean = false) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val settings = ServiceLocator.settings
        if (!settings.get(com.homehub.data.settings.ServiceId.IMMICH).isConfigured) return@launch
        val context = ServiceLocator.appContext
        val resolver = context.contentResolver

        val granted = mediaPermissionGranted(context)
        val allOnDevice = com.homehub.data.local.LocalMedia.query(
            context,
            buckets = settings.autoUploadBuckets,
            includeVideos = settings.autoUploadVideos
        )
        val folderInfo = if (settings.autoUploadBuckets.isEmpty()) "alle Ordner" else "${settings.autoUploadBuckets.size} Ordner"
        val items = allOnDevice
            .filter { it.mediaId !in settings.uploadedMediaIds }
            .take(400) // Obergrenze, damit Hashing & Check flott bleiben

        if (items.isEmpty()) {
            val status = if (!granted) "Kein Medienzugriff erlaubt"
            else "Geraet: ${allOnDevice.size} Medien ($folderInfo), 0 neu"
            _state.update { it.copy(localPending = emptyList(), localScanStatus = status) }
            if (report) {
                val msg = if (!granted)
                    "Kein Medienzugriff erlaubt - bitte in den App-Einstellungen 'Fotos und Videos' erlauben."
                else
                    "Geraet: ${allOnDevice.size} Medien gefunden ($folderInfo), alle bereits gesichert."
                _state.update { it.copy(info = msg) }
            }
            return@launch
        }

        // SHA-1 berechnen (mit Cache)
        val withSha = items.mapNotNull { item ->
            val sha = settings.cachedSha(item.mediaId)
                ?: com.homehub.data.local.LocalMedia.sha1(resolver, item.uri)
                    ?.also { settings.cacheSha(item.mediaId, it) }
            sha?.let { item to it }
        }

        // Auf dem Server pruefen, was schon existiert
        val alreadyUploaded = mutableSetOf<String>()
        withSha.chunked(100).forEach { batch ->
            runCatching {
                repo.api.bulkUploadCheck(
                    com.homehub.data.immich.UploadCheckRequest(
                        batch.map { (item, sha) -> com.homehub.data.immich.UploadCheckItem(item.mediaId, sha) }
                    )
                )
            }.onSuccess { resp ->
                resp.results.filter { it.action.equals("reject", true) }
                    .forEach { alreadyUploaded += it.id }
            }
        }
        settings.markUploaded(alreadyUploaded)

        val pending = withSha.map { it.first }.filter { it.mediaId !in alreadyUploaded }
        _state.update { it.copy(localPending = pending, localScanStatus = "Geraet: ${allOnDevice.size} Medien ($folderInfo), ${pending.size} neu") }
        if (report) {
            _state.update { it.copy(info = "Geraet: ${allOnDevice.size} Medien, davon ${pending.size} noch nicht gesichert.") }
        }
    }

    private fun mediaPermissionGranted(context: android.content.Context): Boolean {
        val perms = if (android.os.Build.VERSION.SDK_INT >= 33)
            listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.any {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Lädt ein einzelnes lokales Medium sofort hoch. */
    fun uploadLocal(item: com.homehub.data.local.LocalItem) = viewModelScope.launch {
        val context = ServiceLocator.appContext
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "homehub"
        val result = repo.uploadFromUri(
            context.contentResolver, item.uri, item.name,
            createdAt = item.createdAtMs, deviceId = deviceId
        )
        result.onSuccess {
            ServiceLocator.settings.markUploaded(listOf(item.mediaId))
            bumpRevision()
            _state.update { s ->
                s.copy(
                    localPending = s.localPending.filterNot { it.mediaId == item.mediaId },
                    info = "Hochgeladen: ${item.name}"
                )
            }
            refreshTimeline()
        }.onFailure { e ->
            _state.update { it.copy(error = "Upload fehlgeschlagen: ${e.message}") }
        }
    }


    // ---------- Karte ----------
    fun loadMap() = viewModelScope.launch {
        runCatching { repo.api.mapMarkers() }
            .onSuccess { m -> _state.update { it.copy(mapMarkers = m) } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    // ---------- Asset-Aktionen ----------
    fun toggleFavorite(asset: Asset, onUpdated: (Asset) -> Unit = {}) = viewModelScope.launch {
        runCatching { repo.api.updateAsset(asset.id, UpdateAssetRequest(isFavorite = !asset.isFavorite)) }
            .onSuccess { updated -> replaceAsset(updated); bumpRevision(); onUpdated(updated) }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun toggleArchive(asset: Asset, onUpdated: (Asset) -> Unit = {}) = viewModelScope.launch {
        val target = !asset.archivedEffective
        runCatching {
            repo.api.updateAsset(
                asset.id,
                UpdateAssetRequest(
                    isArchived = target,
                    // Neuere Server archivieren über visibility
                    visibility = if (target) "archive" else "timeline"
                )
            )
        }
            .onSuccess { updated ->
                if (updated.archivedEffective) removeFromBrowse(updated.id) else replaceAsset(updated)
                bumpRevision()
                onUpdated(updated)
                _state.update { it.copy(info = if (updated.archivedEffective) "Ins Archiv verschoben" else "Aus dem Archiv geholt") } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun trashAsset(asset: Asset, onDone: () -> Unit = {}) = viewModelScope.launch {
        // force = false -> Asset landet im Papierkorb statt endgültig gelöscht zu werden
        runCatching { repo.api.deleteAssets(BulkIds(ids = listOf(asset.id), force = false)) }
            .onSuccess { removeAsset(asset.id); bumpRevision(); _state.update { it.copy(info = "In den Papierkorb verschoben") }; onDone() }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun restoreAsset(asset: Asset) = viewModelScope.launch {
        runCatching { repo.api.restoreAssets(BulkIds(ids = listOf(asset.id))) }
            .onSuccess { removeAsset(asset.id); bumpRevision(); refreshTimeline(); _state.update { it.copy(info = "Wiederhergestellt") } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun emptyTrash() = viewModelScope.launch {
        runCatching { repo.api.emptyTrash() }
            .onSuccess { bumpRevision(); _state.update { it.copy(libraryAssets = emptyList(), info = "Papierkorb geleert") } }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    /** Erstellt einen öffentlichen Share-Link und gibt die URL zurück. */
    fun shareAsset(asset: Asset, onLink: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val link = repo.api.createSharedLink(
                CreateSharedLinkRequest(type = "INDIVIDUAL", assetIds = listOf(asset.id))
            )
            repo.sharedLinkUrl(link)
        }
            .onSuccess { url -> if (url != null) onLink(url) }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    fun shareAlbum(albumId: String, onLink: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val link = repo.api.createSharedLink(CreateSharedLinkRequest(type = "ALBUM", albumId = albumId))
            repo.sharedLinkUrl(link)
        }
            .onSuccess { url -> if (url != null) onLink(url) }
            .onFailure { e -> _state.update { it.copy(error = friendly(e)) } }
    }

    // ---------- Hilfen ----------
    private fun replaceAsset(updated: Asset) = _state.update { s ->
        s.copy(
            buckets = s.buckets.map { b -> b.copy(assets = b.assets?.map { if (it.id == updated.id) updated else it }) },
            searchResults = s.searchResults.map { if (it.id == updated.id) updated else it },
            libraryAssets = s.libraryAssets.map { if (it.id == updated.id) updated else it }
        )
    }

    private fun removeAsset(id: String) = _state.update { s ->
        s.copy(
            buckets = s.buckets.map { b -> b.copy(assets = b.assets?.filterNot { it.id == id }) },
            searchResults = s.searchResults.filterNot { it.id == id },
            libraryAssets = s.libraryAssets.filterNot { it.id == id }
        )
    }

    fun findAsset(id: String): Asset? {
        val s = _state.value
        return s.buckets.firstNotNullOfOrNull { b -> b.assets?.firstOrNull { it.id == id } }
            ?: s.searchResults.firstOrNull { it.id == id }
            ?: s.libraryAssets.firstOrNull { it.id == id }
            ?: s.albumDetail?.assets?.firstOrNull { it.id == id }
    }

    private fun friendly(e: Throwable): String = when {
        e.message?.contains("401") == true -> "Immich: API-Key ungültig. Bitte in den Einstellungen prüfen."
        e is IllegalStateException -> e.message ?: "Immich ist nicht konfiguriert."
        else -> "Immich nicht erreichbar: ${e.message ?: "unbekannter Fehler"}"
    }
}
