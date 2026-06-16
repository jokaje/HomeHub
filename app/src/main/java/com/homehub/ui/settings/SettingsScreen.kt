package com.homehub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.homehub.core.ServiceLocator
import com.homehub.data.settings.ServiceConfig
import com.homehub.data.settings.ServiceId
import com.homehub.work.AutoUploadWorker
import kotlinx.coroutines.launch

private data class FieldSpec(
    val tokenLabel: String,
    val extraLabel: String? = null,
    val hint: String? = null
)

private val specs = mapOf(
    ServiceId.IMMICH to FieldSpec(
        tokenLabel = "API-Key",
        hint = "API-Key erstellen: Immich → Kontoeinstellungen → API-Schlüssel"
    ),
    ServiceId.HOME_ASSISTANT to FieldSpec(
        tokenLabel = "Long-Lived-Token (optional)",
        hint = "Login erfolgt im integrierten Browser. Token nur für Status-Checks nötig."
    ),
    ServiceId.HERMES to FieldSpec(
        tokenLabel = "API-Key (optional)",
        extraLabel = "Modellname",
        hint = "OpenAI-kompatible API: {URL}/v1/chat/completions"
    ),
    ServiceId.OPEN_WEBUI to FieldSpec(
        tokenLabel = "API-Key (optional)",
        hint = "Login erfolgt im integrierten Browser."
    ),
    ServiceId.COMFYUI to FieldSpec(
        tokenLabel = "API-Key (optional)",
        hint = "Der Workflow wird auf der ComfyUI-Seite hinterlegt."
    ),
    ServiceId.NAVIDROME to FieldSpec(
        tokenLabel = "Passwort",
        extraLabel = "Benutzername",
        hint = "Navidrome-Zugangsdaten. Übliche Adresse: https://musik.deinedomain.de"
    ),
    ServiceId.JELLYFIN to FieldSpec(
        tokenLabel = "Passwort",
        extraLabel = "Benutzername",
        hint = "Jellyfin-Zugangsdaten. Übliche Adresse: https://jellyfin.deinedomain.de"
    )
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var autoUpload by remember { mutableStateOf(ServiceLocator.settings.autoUploadEnabled) }
    var autoUploadVideos by remember { mutableStateOf(ServiceLocator.settings.autoUploadVideos) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var deviceFolders by remember { mutableStateOf<List<DeviceFolder>>(emptyList()) }
    val mediaPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Ergebnis wird beim nächsten Öffnen des Dialogs berücksichtigt */ }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

            // Darstellung
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Darstellung", style = MaterialTheme.typography.titleMedium)
                    val mode by ServiceLocator.settings.themeMode.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = mode == "light",
                            onClick = { ServiceLocator.settings.setThemeMode("light") },
                            label = { Text("Hell") }
                        )
                        androidx.compose.material3.FilterChip(
                            selected = mode == "dark",
                            onClick = { ServiceLocator.settings.setThemeMode("dark") },
                            label = { Text("Dunkel") }
                        )
                        androidx.compose.material3.FilterChip(
                            selected = mode == "system",
                            onClick = { ServiceLocator.settings.setThemeMode("system") },
                            label = { Text("System") }
                        )
                    }
                }
            }

            // Untere Navigation: bis zu 4 Tabs wählen
            BottomTabsCard()

            Text(
                "Pro Dienst kannst du eine lokale Adresse (Heimnetz) und eine Remote-Adresse " +
                    "(unterwegs) hinterlegen. HomeHub prüft automatisch, was erreichbar ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ServiceId.entries.forEach { id ->
                ServiceSection(id) { scope.launch { snackbar.showSnackbar("${id.title} gespeichert") } }
            }

            // Auto-Upload
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Immich Auto-Upload", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Neue Fotos automatisch sichern")
                            Text(
                                "Lädt regelmäßig neue Aufnahmen zu Immich hoch (nur im WLAN).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoUpload, onCheckedChange = { on ->
                            autoUpload = on
                            ServiceLocator.settings.autoUploadEnabled = on
                            if (on) {
                                mediaPermission.launch(requiredMediaPermissions())
                                AutoUploadWorker.schedule(context)
                            } else AutoUploadWorker.cancel(context)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auch Videos hochladen", Modifier.weight(1f))
                        Switch(checked = autoUploadVideos, enabled = autoUpload, onCheckedChange = {
                            autoUploadVideos = it
                            ServiceLocator.settings.autoUploadVideos = it
                        })
                    }
                    // Ordnerauswahl
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Zu sichernde Ordner")
                            val sel = ServiceLocator.settings.autoUploadBuckets
                            Text(
                                if (sel.isEmpty()) "Alle Ordner" else "${sel.size} Ordner ausgewählt",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.TextButton(
                            enabled = autoUpload,
                            onClick = {
                                mediaPermission.launch(requiredMediaPermissions())
                                deviceFolders = loadDeviceFolders(context)
                                showFolderDialog = true
                            }
                        ) { Text("Auswählen") }
                    }
                    if (autoUpload) {
                        Button(onClick = { AutoUploadWorker.runNow(context) }) {
                            Text("Jetzt sichern")
                        }
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
        SnackbarHost(snackbar)
    }

    if (showFolderDialog) {
        FolderPickerDialog(
            folders = deviceFolders,
            initiallySelected = ServiceLocator.settings.autoUploadBuckets,
            onDismiss = { showFolderDialog = false },
            onConfirm = { selected ->
                ServiceLocator.settings.autoUploadBuckets = selected
                showFolderDialog = false
            }
        )
    }
}

/** Geräte-Ordner (z.B. Kamera, Screenshots, WhatsApp) aus dem MediaStore lesen. */
data class DeviceFolder(val id: String, val name: String, val count: Int)

private fun loadDeviceFolders(context: android.content.Context): List<DeviceFolder> {
    val folders = LinkedHashMap<String, Pair<String, Int>>() // id -> (name, count)
    fun scan(collection: android.net.Uri) {
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf("bucket_id", "bucket_display_name"),
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex("bucket_id")
                val nameCol = cursor.getColumnIndex("bucket_display_name")
                if (idCol < 0) return@use
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol) ?: continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Unbekannt" else "Unbekannt"
                    val prev = folders[id]
                    folders[id] = name to ((prev?.second ?: 0) + 1)
                }
            }
        }
    }
    scan(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    scan(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    return folders.map { (id, v) -> DeviceFolder(id, v.first, v.second) }
        .sortedByDescending { it.count }
}

private fun requiredMediaPermissions(): Array<String> =
    if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    ) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
private fun FolderPickerDialog(
    folders: List<DeviceFolder>,
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordner auswählen") },
        text = {
            Column {
                Text(
                    "Keine Auswahl = alle Ordner werden gesichert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folders.isEmpty()) {
                    Text(
                        "Keine Ordner gefunden. Bitte zuerst die Medien-Berechtigung erteilen " +
                            "und erneut öffnen.",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp).padding(top = 8.dp)
                ) {
                    items(folders.size) { i ->
                        val folder = folders[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = folder.id in selected,
                                onCheckedChange = { on ->
                                    selected = if (on) selected + folder.id else selected - folder.id
                                }
                            )
                            Column {
                                Text(folder.name)
                                Text(
                                    "${folder.count} Elemente",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(selected) }) { Text("Übernehmen") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun ServiceSection(id: ServiceId, onSaved: () -> Unit) {
    val spec = specs.getValue(id)
    val saved = ServiceLocator.settings.get(id)
    var local by remember(id) { mutableStateOf(saved.localUrl) }
    var remote by remember(id) { mutableStateOf(saved.remoteUrl) }
    var token by remember(id) { mutableStateOf(saved.token) }
    var extra by remember(id) { mutableStateOf(saved.extra) }
    var dirty by remember(id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(id.title, style = MaterialTheme.typography.titleMedium)
            spec.hint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = local, onValueChange = { local = it; dirty = true },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Lokale URL") },
                placeholder = { Text("http://192.168.1.10:2283") }
            )
            OutlinedTextField(
                value = remote, onValueChange = { remote = it; dirty = true },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Remote-URL") },
                placeholder = { Text("https://dienst.meinedomain.de") }
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it; dirty = true },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(spec.tokenLabel) },
                visualTransformation = PasswordVisualTransformation()
            )
            // "extra" nur anzeigen, wo es eine Bedeutung hat (z.B. Hermes-Modell).
            // Bei ComfyUI speichert das extra-Feld den Workflow und wird dort gepflegt.
            spec.extraLabel?.let { label ->
                OutlinedTextField(
                    value = extra, onValueChange = { extra = it; dirty = true },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(label) }
                )
            }
            Button(
                enabled = dirty,
                onClick = {
                    ServiceLocator.settings.save(
                        id,
                        ServiceConfig(localUrl = local, remoteUrl = remote, token = token,
                            extra = if (spec.extraLabel != null) extra else ServiceLocator.settings.get(id).extra)
                    )
                    ServiceLocator.urls.invalidate(id)
                    dirty = false
                    onSaved()
                }
            ) { Text("Speichern") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BottomTabsCard() {
    val selected by ServiceLocator.settings.bottomTabs.collectAsState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Untere Leiste", style = MaterialTheme.typography.titleMedium)
            Text(
                "Wähle bis zu 4 Bereiche für die untere Leiste. Alles andere bleibt über \"Mehr\" erreichbar. (${selected.size}/4)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.homehub.ALL_DESTINATIONS.forEach { dest ->
                    val isOn = dest.route in selected
                    val atLimit = selected.size >= 4 && !isOn
                    androidx.compose.material3.FilterChip(
                        selected = isOn,
                        enabled = !atLimit,
                        onClick = {
                            val next = if (isOn) selected - dest.route else selected + dest.route
                            ServiceLocator.settings.setBottomTabs(next)
                        },
                        label = { Text(dest.label) },
                        leadingIcon = { androidx.compose.material3.Icon(dest.icon, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}
