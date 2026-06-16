package com.homehub.ui.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehub.core.ServiceLocator
import com.homehub.data.jellyfin.JellyItem
import com.homehub.data.settings.ServiceId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Eine thematische Reihe auf der Startseite (z. B. 'Neu in Filme'). */
data class JellyRow(
    val title: String,
    val items: List<JellyItem>,
    val landscape: Boolean = false,
    val ranked: Boolean = false
)

data class JellyfinUiState(
    val configured: Boolean = true,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val authFailed: Boolean = false,
    val featured: List<JellyItem> = emptyList(),
    val resume: List<JellyItem> = emptyList(),
    val nextUp: List<JellyItem> = emptyList(),
    val top10: List<JellyItem> = emptyList(),
    val rows: List<JellyRow> = emptyList(),
    val views: List<JellyItem> = emptyList(),
    // Navigation
    val openParent: JellyItem? = null,
    val openItems: List<JellyItem> = emptyList(),
    val openDetail: JellyItem? = null,
    val seasons: List<JellyItem> = emptyList(),
    val selectedSeason: JellyItem? = null,
    val episodes: List<JellyItem> = emptyList(),
    val showDownloads: Boolean = false,
    // Suche
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<JellyItem> = emptyList(),
    val error: String? = null
)

class JellyfinViewModel : ViewModel() {
    private val repo = ServiceLocator.jellyfin
    private val downloadsRepo = ServiceLocator.jellyfinDownloads

    private val _state = MutableStateFlow(JellyfinUiState())
    val state: StateFlow<JellyfinUiState> = _state

    val downloads = downloadsRepo.state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val cfg = ServiceLocator.settings.get(ServiceId.JELLYFIN)
        if (!cfg.isConfigured) { _state.update { it.copy(configured = false) }; return@launch }
        _state.update { it.copy(configured = true, loading = it.featured.isEmpty(), refreshing = true, authFailed = false, error = null) }
        if (!repo.ensureAuth()) {
            _state.update { it.copy(loading = false, refreshing = false, authFailed = true) }
            return@launch
        }
        val uid = repo.userId ?: return@launch
        runCatching {
            val views = repo.api.getViews(uid).items
            val resume = runCatching { repo.api.getResume(uid, 16).items }.getOrDefault(emptyList())
            val nextUp = runCatching { repo.api.getNextUp(uid, 16).items }.getOrDefault(emptyList())
            val latestAll = runCatching { repo.api.getLatest(uid, null, 14) }.getOrDefault(emptyList())
            val top10 = runCatching {
                repo.api.getItems(
                    uid, includeItemTypes = "Movie,Series", recursive = true,
                    sortBy = "CommunityRating", sortOrder = "Descending", limit = 10
                ).items
            }.getOrDefault(emptyList())
            val rows = mutableListOf<JellyRow>()
            for (view in views.filter { it.collectionType == "movies" || it.collectionType == "tvshows" }) {
                val latest = runCatching { repo.api.getLatest(uid, view.id, 16) }.getOrDefault(emptyList())
                if (latest.isNotEmpty()) rows.add(JellyRow("Neu in ${view.name}", latest))
            }
            val featured = latestAll.ifEmpty { resume }.take(7)
            Loaded(views, resume, nextUp, top10, rows, featured)
        }.onSuccess { r ->
            _state.update {
                it.copy(
                    loading = false, refreshing = false, views = r.views, resume = r.resume,
                    nextUp = r.nextUp, top10 = r.top10, rows = r.rows, featured = r.featured
                )
            }
            loadGenreRows(uid)
        }.onFailure { e ->
            _state.update { it.copy(loading = false, refreshing = false, error = e.message ?: "Fehler beim Laden") }
        }
    }

    private fun loadGenreRows(uid: String) = viewModelScope.launch {
        val genres = runCatching { repo.api.getGenres(uid).items }.getOrDefault(emptyList())
            .filter { it.name.isNotBlank() }.take(10)
        for (g in genres) {
            val items = runCatching {
                repo.api.getItems(
                    uid, includeItemTypes = "Movie,Series", recursive = true,
                    sortBy = "CommunityRating", sortOrder = "Descending", genres = g.name, limit = 16
                ).items
            }.getOrDefault(emptyList())
            if (items.size >= 3) {
                _state.update { st ->
                    if (st.rows.any { it.title == "Top ${g.name}" }) st
                    else st.copy(rows = st.rows + JellyRow("Top ${g.name}", items))
                }
            }
        }
    }

    fun openParent(item: JellyItem) = viewModelScope.launch {
        _state.update { it.copy(openParent = item, openItems = emptyList(), loading = true) }
        val uid = repo.userId ?: return@launch
        val types = when (item.collectionType) {
            "movies" -> "Movie"
            "tvshows" -> "Series"
            else -> null
        }
        runCatching {
            repo.api.getItems(uid, parentId = item.id, includeItemTypes = types, recursive = false).items
        }.onSuccess { items -> _state.update { it.copy(loading = false, openItems = items) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun closeParent() = _state.update { it.copy(openParent = null, openItems = emptyList()) }

    fun openDetail(item: JellyItem) = viewModelScope.launch {
        _state.update {
            it.copy(openDetail = item, episodes = emptyList(), seasons = emptyList(), selectedSeason = null, loading = item.type == "Series")
        }
        val uid = repo.userId ?: return@launch
        runCatching { repo.api.getItem(uid, item.id) }.getOrNull()?.let { fresh ->
            _state.update { it.copy(openDetail = fresh) }
        }
        if (item.type == "Series") {
            val seasons = runCatching { repo.api.getSeasons(item.id, uid).items }.getOrDefault(emptyList())
            _state.update { it.copy(seasons = seasons) }
            seasons.firstOrNull()?.let { selectSeason(it) }
                ?: run {
                    // Keine Staffeln -> alle Episoden flach laden
                    val eps = runCatching {
                        repo.api.getItems(uid, parentId = item.id, includeItemTypes = "Episode", recursive = true, sortBy = "ParentIndexNumber,IndexNumber").items
                    }.getOrDefault(emptyList())
                    _state.update { it.copy(loading = false, episodes = eps) }
                }
        }
    }

    fun selectSeason(season: JellyItem) = viewModelScope.launch {
        val uid = repo.userId ?: return@launch
        _state.update { it.copy(selectedSeason = season, loading = true) }
        val eps = runCatching { repo.api.getEpisodes(season.seriesId ?: season.id, uid, season.id).items }
            .getOrDefault(emptyList())
        _state.update { it.copy(loading = false, episodes = eps) }
    }

    fun closeDetail() = _state.update { it.copy(openDetail = null, episodes = emptyList(), seasons = emptyList(), selectedSeason = null) }

    fun toggleFavorite(item: JellyItem) = viewModelScope.launch {
        val newFav = !(item.userData?.isFavorite ?: false)
        repo.setFavorite(item.id, newFav)
        _state.value.openDetail?.let { d ->
            if (d.id == item.id) {
                _state.update { it.copy(openDetail = d.copy(userData = (d.userData ?: com.homehub.data.jellyfin.UserData()).copy(isFavorite = newFav))) }
            }
        }
    }

    // --- Suche ---
    private var searchJob: Job? = null
    fun openSearch() = _state.update { it.copy(showSearch = true) }
    fun closeSearch() = _state.update { it.copy(showSearch = false, searchQuery = "", searchResults = emptyList()) }

    fun onSearchQuery(q: String) {
        _state.update { it.copy(searchQuery = q) }
        searchJob?.cancel()
        if (q.isBlank()) { _state.update { it.copy(searchResults = emptyList(), searching = false) }; return }
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _state.update { it.copy(searching = true) }
            val uid = repo.userId
            val results = if (uid == null) emptyList() else runCatching {
                repo.api.getItems(
                    uid, includeItemTypes = "Movie,Series,Episode", recursive = true,
                    searchTerm = q, limit = 50
                ).items
            }.getOrDefault(emptyList())
            _state.update { it.copy(searching = false, searchResults = results) }
        }
    }

    // --- Downloads ---
    fun download(item: JellyItem) = viewModelScope.launch { downloadsRepo.download(item) }
    fun deleteDownload(id: String) = viewModelScope.launch { downloadsRepo.delete(id) }
    fun isDownloaded(id: String) = downloadsRepo.isDownloaded(id)
    fun downloadedItems(): List<JellyItem> = downloadsRepo.downloadedItems()

    fun openDownloads() = _state.update { it.copy(showDownloads = true) }
    fun closeDownloads() = _state.update { it.copy(showDownloads = false) }

    fun dismissError() = _state.update { it.copy(error = null) }
}

private data class Loaded(
    val views: List<JellyItem>,
    val resume: List<JellyItem>,
    val nextUp: List<JellyItem>,
    val top10: List<JellyItem>,
    val rows: List<JellyRow>,
    val featured: List<JellyItem>
)
