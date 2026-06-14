package com.homehub

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homehub.core.ServiceLocator
import com.homehub.data.settings.ServiceId
import com.homehub.ui.comfy.ComfyScreen
import com.homehub.ui.dashboard.DashboardScreen
import com.homehub.ui.hermes.HermesScreen
import com.homehub.ui.immich.AlbumDetailScreen
import com.homehub.ui.immich.AssetViewerScreen
import com.homehub.ui.immich.ImmichHomeScreen
import com.homehub.ui.immich.ImmichViewModel
import com.homehub.ui.navidrome.MiniPlayer
import com.homehub.ui.navidrome.NavidromeScreen
import com.homehub.ui.navidrome.NowPlayingScreen
import com.homehub.ui.settings.SettingsScreen
import com.homehub.ui.theme.HomeHubTheme
import com.homehub.ui.web.WebScreen
import com.homehub.work.AutoUploadWorker

/**
 * FragmentActivity statt ComponentActivity, damit BiometricPrompt
 * (gesperrter Ordner) funktioniert.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (ServiceLocator.settings.autoUploadEnabled) {
            AutoUploadWorker.schedule(this)
        }
        setContent {
            HomeHubTheme { HomeHubRoot() }
        }
    }
}

data class Destination(val route: String, val label: String, val subtitle: String, val icon: ImageVector)

/** Alle wählbaren Bereiche (für Bottom-Tabs und das "Mehr"-Menü). */
val ALL_DESTINATIONS = listOf(
    Destination("dashboard", "Start", "Dashboard & Status", Icons.Default.Dashboard),
    Destination("hermes", "Hermes", "Sprich mit deinem Assistenten", Icons.Default.SmartToy),
    Destination("immich", "Fotos", "Fotos & Videos", Icons.Default.Photo),
    Destination("navidrome", "Musik", "Navidrome streamen", Icons.Default.MusicNote),
    Destination("homeassistant", "Home", "Smart Home steuern", Icons.Default.Home),
    Destination("openwebui", "Open WebUI", "Chat mit deinem LLM", Icons.Default.Chat),
    Destination("comfy", "ComfyUI", "Bilder generieren", Icons.Default.Brush)
)

fun destinationFor(route: String): Destination? = ALL_DESTINATIONS.firstOrNull { it.route == route }

@Composable
private fun stateListSaver() = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() }
)

@Composable
private fun HomeHubRoot() {
    val tabHistory = rememberSaveable(saver = stateListSaver()) { mutableStateListOf("dashboard") }
    val overlays = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<String>() }
    val stateHolder = rememberSaveableStateHolder()
    val current = tabHistory.last()

    val immichVm: ImmichViewModel = viewModel()

    // Bottom-Tabs aus den Einstellungen (reaktiv) + immer "Mehr"
    val bottomTabs by ServiceLocator.settings.bottomTabs.collectAsState()
    val bottomRoutes = bottomTabs

    fun open(route: String) {
        if (route == current) return
        tabHistory.remove(route)
        tabHistory.add(route)
    }

    BackHandler(enabled = overlays.isNotEmpty() || tabHistory.size > 1) {
        if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex)
        else tabHistory.removeAt(tabHistory.lastIndex)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    // Mini-Player über der Navigation, falls Musik läuft
                    MiniPlayer(onExpand = { if (!overlays.contains("nowplaying")) overlays.add("nowplaying") })

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp
                    ) {
                        bottomRoutes.forEach { route ->
                            val dest = destinationFor(route) ?: return@forEach
                            NavigationBarItem(
                                selected = current == route,
                                onClick = { open(route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                                colors = navColors()
                            )
                        }
                        // "Mehr": ausgewählt, sobald man auf einem nicht angehefteten Bereich ist
                        NavigationBarItem(
                            selected = current !in bottomRoutes,
                            onClick = { open("more") },
                            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "Mehr") },
                            label = { Text("Mehr", style = MaterialTheme.typography.labelMedium) },
                            colors = navColors()
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                stateHolder.SaveableStateProvider(current) {
                    when (current) {
                        "dashboard" -> DashboardScreen(
                            onNavigate = { open(it) },
                            onOpenMemory = { asset, list ->
                                immichVm.viewerList = list
                                overlays.add("asset/${asset.id}")
                            }
                        )
                        "hermes" -> HermesScreen()
                        "immich" -> ImmichHomeScreen(
                            vm = immichVm,
                            onOpenAsset = { asset -> overlays.add("asset/${asset.id}") },
                            onOpenAlbum = { id -> overlays.add("album/$id") }
                        )
                        "navidrome" -> NavidromeScreen()
                        "homeassistant" -> WebScreen(ServiceId.HOME_ASSISTANT)
                        "openwebui" -> WebScreen(ServiceId.OPEN_WEBUI)
                        "comfy" -> ComfyScreen()
                        "settings" -> SettingsScreen()
                        "more" -> MoreScreen(bottomRoutes = bottomRoutes, onNavigate = { open(it) })
                    }
                }
            }
        }

        overlays.lastOrNull()?.let { overlay ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    overlay == "nowplaying" -> NowPlayingScreen(onBack = { overlays.removeAt(overlays.lastIndex) })
                    overlay.startsWith("asset/") -> AssetViewerScreen(
                        assetId = overlay.removePrefix("asset/"),
                        vm = immichVm,
                        onBack = { overlays.removeAt(overlays.lastIndex) }
                    )
                    overlay.startsWith("album/") -> AlbumDetailScreen(
                        albumId = overlay.removePrefix("album/"),
                        vm = immichVm,
                        onOpenAsset = { asset -> overlays.add("asset/${asset.id}") },
                        onBack = { overlays.removeAt(overlays.lastIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun MoreScreen(bottomRoutes: List<String>, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mehr", style = MaterialTheme.typography.headlineMedium)
        // Alle Bereiche, die NICHT in der unteren Leiste angeheftet sind
        ALL_DESTINATIONS.filter { it.route !in bottomRoutes }.forEach { dest ->
            MoreEntry(dest.label, dest.subtitle, dest.icon) { onNavigate(dest.route) }
        }
        MoreEntry("Einstellungen", "Dienste, Tabs, Darstellung, Auto-Upload", Icons.Default.Settings) { onNavigate("settings") }
    }
}

@Composable
private fun MoreEntry(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
