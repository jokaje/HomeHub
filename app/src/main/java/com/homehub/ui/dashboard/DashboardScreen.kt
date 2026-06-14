package com.homehub.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homehub.core.ServiceLocator
import com.homehub.data.network.Http
import com.homehub.data.settings.ServiceId
import com.homehub.ui.theme.Danger
import com.homehub.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.time.LocalTime

enum class Status { UNKNOWN, ONLINE, OFFLINE, UNCONFIGURED }

data class DashboardState(
    val statuses: Map<ServiceId, Status> = ServiceId.entries.associateWith { Status.UNKNOWN },
    val memories: List<com.homehub.data.immich.Asset> = emptyList(),
    val checking: Boolean = false
)

class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        refresh()
        loadMemories()
        // Beim Kaltstart braucht der erste Verbindungsaufbau (DNS/TLS) oft länger
        // als der Probe-Timeout -> nach kurzer Zeit automatisch erneut prüfen.
        viewModelScope.launch {
            kotlinx.coroutines.delay(2500)
            if (_state.value.statuses.values.any { it == Status.OFFLINE }) refresh()
            if (_state.value.memories.isEmpty()) loadMemories()
        }
    }

    /**
     * Erinnerungen für die Diashow: bevorzugt Immichs "An diesem Tag"-Memories,
     * sonst die neuesten Fotos der Bibliothek.
     */
    fun loadMemories() = viewModelScope.launch {
        if (!ServiceLocator.settings.get(ServiceId.IMMICH).isConfigured) return@launch
        val fromMemories = runCatching { ServiceLocator.immich.api.memories() }
            .getOrNull()?.flatMap { it.assets }.orEmpty()
        val assets = fromMemories.ifEmpty {
            runCatching {
                ServiceLocator.immich.api.metadataSearch(
                    com.homehub.data.immich.MetadataSearchRequest(size = 40)
                )
            }.getOrNull()?.assets?.items.orEmpty()
        }
            .filter { !it.isVideo && !it.isTrashed && !it.archivedEffective && !it.lockedCustom }
            .shuffled()
            .take(30)
        _state.update { it.copy(memories = assets) }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(checking = true) }
        ServiceLocator.urls.invalidate()
        val results = ServiceId.entries.map { id ->
            async { id to check(id) }
        }.map { it.await() }.toMap()
        _state.update { it.copy(statuses = results, checking = false) }
    }

    private suspend fun check(id: ServiceId): Status = withContext(Dispatchers.IO) {
        val cfg = ServiceLocator.settings.get(id)
        if (!cfg.isConfigured) return@withContext Status.UNCONFIGURED
        val base = ServiceLocator.urls.baseUrl(id) ?: return@withContext Status.OFFLINE
        // Zwei Versuche: der erste scheitert nach App-Start gern am kalten Verbindungsaufbau
        repeat(2) { attempt ->
            try {
                val req = Request.Builder().url(base).get().build()
                Http.probeClient.newCall(req).execute().use { return@withContext Status.ONLINE }
            } catch (_: Exception) {
                if (attempt == 0) kotlinx.coroutines.delay(400)
            }
        }
        Status.OFFLINE
    }
}

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    onOpenMemory: (com.homehub.data.immich.Asset, List<com.homehub.data.immich.Asset>) -> Unit = { _, _ -> },
    vm: DashboardViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                val online = state.statuses.values.count { it == Status.ONLINE }
                val total = state.statuses.values.count { it != Status.UNCONFIGURED }
                Text(
                    if (total == 0) "Richte deine Dienste in den Einstellungen ein."
                    else "$online von $total Diensten erreichbar",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.refresh(); vm.loadMemories() }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Refresh, "Status aktualisieren", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Erinnerungen-Diashow
        if (state.memories.isNotEmpty()) {
            MemoryCarousel(
                memories = state.memories,
                onOpen = { asset -> onOpenMemory(asset, state.memories) }
            )
        }

        SectionHeader("Übersicht", "Deine Dienste")

        ServiceCard(ServiceId.HERMES, Icons.Default.SmartToy, "Sprich mit deinem Assistenten",
            state.statuses[ServiceId.HERMES]) { onNavigate("hermes") }
        ServiceCard(ServiceId.IMMICH, Icons.Default.Photo, "Fotos & Videos",
            state.statuses[ServiceId.IMMICH]) { onNavigate("immich") }
        ServiceCard(ServiceId.NAVIDROME, Icons.Default.MusicNote, "Musik streamen",
            state.statuses[ServiceId.NAVIDROME]) { onNavigate("navidrome") }
        ServiceCard(ServiceId.HOME_ASSISTANT, Icons.Default.Home, "Smart Home steuern",
            state.statuses[ServiceId.HOME_ASSISTANT]) { onNavigate("homeassistant") }
        ServiceCard(ServiceId.OPEN_WEBUI, Icons.Default.Chat, "Chat mit deinem LLM",
            state.statuses[ServiceId.OPEN_WEBUI]) { onNavigate("openwebui") }
        ServiceCard(ServiceId.COMFYUI, Icons.Default.Palette, "Bilder generieren",
            state.statuses[ServiceId.COMFYUI]) { onNavigate("comfy") }
    }
}

@Composable
private fun ServiceCard(
    id: ServiceId,
    icon: ImageVector,
    subtitle: String,
    status: Status?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                // grünes Eyebrow-Label + fetter Titel – nzb360-Kartenmuster
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(id.title, style = MaterialTheme.typography.titleMedium)
            }
            StatusDot(status ?: Status.UNKNOWN)
        }
    }
}


/**
 * Auto-Diashow mit Erinnerungen aus Immich. Wechselt alle 6 Sekunden
 * mit weichem Übergang; Antippen öffnet das Bild im Vollbild-Viewer.
 */
@Composable
private fun MemoryCarousel(
    memories: List<com.homehub.data.immich.Asset>,
    onOpen: (com.homehub.data.immich.Asset) -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(memories.size) {
        while (true) {
            kotlinx.coroutines.delay(6000)
            index = (index + 1) % memories.size
        }
    }
    val asset = memories[index % memories.size]

    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onOpen(asset) }
    ) {
        androidx.compose.animation.Crossfade(
            targetState = asset,
            animationSpec = androidx.compose.animation.core.tween(900),
            label = "memories"
        ) { current ->
            var url by remember(current.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(current.id) {
                url = com.homehub.core.ServiceLocator.immich.thumbnailUrl(current.id, size = "preview")
            }
            val loader = com.homehub.ui.immich.rememberImmichImageLoader()
            Box(Modifier.fillMaxSize()) {
                url?.let {
                    coil.compose.AsyncImage(
                        model = it,
                        imageLoader = loader,
                        contentDescription = "Erinnerung",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Weicher Verlauf für die Bildunterschrift
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(
                        "Erinnerungen",
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        memoryCaption(current.fileCreatedAt),
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}

private fun memoryCaption(iso: String): String = runCatching {
    val date = java.time.OffsetDateTime.parse(iso)
    date.format(java.time.format.DateTimeFormatter.ofPattern("d. MMMM yyyy", java.util.Locale.GERMAN))
}.getOrElse { iso.take(10) }

@Composable
private fun SectionHeader(eyebrow: String, title: String) {
    Column {
        // kurzer grüner Strich – nzb360-Signatur über Abschnittstiteln
        Box(
            Modifier
                .padding(bottom = 8.dp)
                .height(3.dp)
                .width(34.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StatusDot(status: Status) {
    val (color, label) = when (status) {
        Status.ONLINE -> Success to "online"
        Status.OFFLINE -> Danger to "offline"
        Status.UNCONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) to "—"
        Status.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to "…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Hallo"
    in 18..22 -> "Guten Abend"
    else -> "Gute Nacht"
}
