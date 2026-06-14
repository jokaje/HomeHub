package com.homehub.ui.comfy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.homehub.core.ServiceLocator
import com.homehub.data.settings.ServiceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComfyUiState(
    val workflowJson: String = "",
    val prompt: String = "",
    val running: Boolean = false,
    val statusText: String = "",
    val imageUrls: List<String> = emptyList(),
    val error: String? = null
)

class ComfyViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        ComfyUiState(workflowJson = ServiceLocator.settings.get(ServiceId.COMFYUI).extra)
    )
    val state: StateFlow<ComfyUiState> = _state

    fun setWorkflow(jsonText: String) {
        _state.update { it.copy(workflowJson = jsonText) }
        // Workflow im "extra"-Feld der ComfyUI-Konfiguration persistieren
        val cfg = ServiceLocator.settings.get(ServiceId.COMFYUI)
        ServiceLocator.settings.save(ServiceId.COMFYUI, cfg.copy(extra = jsonText))
    }

    fun setPrompt(p: String) = _state.update { it.copy(prompt = p) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun run() = viewModelScope.launch {
        val s = _state.value
        if (s.workflowJson.isBlank()) {
            _state.update { it.copy(error = "Bitte zuerst einen Workflow (API-Format-JSON) einfügen.") }
            return@launch
        }
        _state.update { it.copy(running = true, statusText = "Workflow wird gesendet …", imageUrls = emptyList()) }

        val submit = ServiceLocator.comfy.submit(s.workflowJson, s.prompt)
        submit.onFailure { e ->
            _state.update { it.copy(running = false, error = e.message) }
        }.onSuccess { promptId ->
            _state.update { it.copy(statusText = "In der Warteschlange … (ID ${promptId.take(8)})") }
            val images = ServiceLocator.comfy.awaitImages(promptId)
            images.onFailure { e ->
                _state.update { it.copy(running = false, error = e.message) }
            }.onSuccess { list ->
                val urls = list.mapNotNull { ServiceLocator.comfy.imageUrl(it) }
                _state.update {
                    it.copy(
                        running = false,
                        statusText = if (urls.isEmpty()) "Fertig – der Workflow hat keine Bilder ausgegeben." else "",
                        imageUrls = urls
                    )
                }
            }
        }
    }
}

@Composable
fun ComfyScreen(vm: ComfyViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showWorkflowEditor by remember { mutableStateOf(state.workflowJson.isBlank()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ComfyUI", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Workflow als API-JSON hinterlegen, Prompt eingeben, ausführen. " +
                "Platzhalter im Workflow: {{PROMPT}} und {{SEED}}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TextButton(onClick = { showWorkflowEditor = !showWorkflowEditor }) {
            Text(if (showWorkflowEditor) "Workflow-Editor ausblenden" else "Workflow bearbeiten")
        }
        if (showWorkflowEditor) {
            OutlinedTextField(
                value = state.workflowJson,
                onValueChange = vm::setWorkflow,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                placeholder = { Text("Workflow-JSON hier einfügen (ComfyUI → Save (API Format))") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = vm::setPrompt,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt") },
            placeholder = { Text("z.B. a cinematic photo of a lighthouse at dawn") }
        )

        Button(onClick = vm::run, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
            if (state.running) {
                CircularProgressIndicator(Modifier.height(18.dp).aspectRatio(1f), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
            }
            Text(if (state.running) "  Läuft …" else "  Ausführen", Modifier.padding(start = 4.dp))
        }

        if (state.statusText.isNotBlank()) {
            Text(state.statusText, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.imageUrls.isNotEmpty()) {
            Text("Ergebnisse", style = MaterialTheme.typography.titleLarge)
            // Grid mit fester Höhe innerhalb der Scroll-Column
            val rows = (state.imageUrls.size + 1) / 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height((rows * 190).dp),
                userScrollEnabled = false,
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.imageUrls) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Generiertes Bild",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        Row { } // unterer Abstand
    }
}
