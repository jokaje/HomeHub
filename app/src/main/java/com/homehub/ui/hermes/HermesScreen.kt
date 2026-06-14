package com.homehub.ui.hermes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homehub.ui.theme.Teal
import com.homehub.ui.theme.Violet

@Composable
fun HermesScreen(vm: HermesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }

    val speech = remember {
        SpeechManager(
            context = context,
            onPartial = vm::onPartial,
            onFinal = { vm.onPartial(""); vm.send(it) },
            onLevel = vm::onLevel,
            onListeningChanged = vm::onListeningChanged,
            onSpeakingChanged = vm::onSpeakingChanged,
            onError = { /* in Snackbar unten */ vm.onPartial("") }
        )
    }
    DisposableEffect(Unit) {
        vm.speak = { speech.speak(it) }
        onDispose { speech.release(); vm.speak = null }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) speech.startListening() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // --- Der Orb ---
        // Ohne Chatverlauf sitzt der Orb exakt in der Bildschirmmitte;
        // sobald Nachrichten da sind, zentriert in der oberen Hälfte.
        val hasChat = state.messages.isNotEmpty()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (hasChat) 0.45f else 1f),
            contentAlignment = Alignment.Center
        ) {
            OrbAvatar(
                state = state.orbState,
                shape = state.orbShape,
                level = state.level,
                modifier = Modifier.size(240.dp)
            )
            // Status / Live-Transkript
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val label = when (state.orbState) {
                    OrbState.LISTENING -> "Ich höre zu …"
                    OrbState.THINKING -> "Hermes denkt nach …"
                    OrbState.SPEAKING -> "Hermes spricht"
                    OrbState.IDLE -> ""
                }
                if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = Teal)
                if (state.partialSpeech.isNotBlank()) {
                    Text(
                        state.partialSpeech,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = vm::toggleTts) {
                    Icon(
                        if (state.ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Sprachausgabe umschalten",
                        tint = if (state.ttsEnabled) Violet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.clearChat(); vm.resetShape(); speech.stopSpeaking() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Chat leeren",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- Chatverlauf ---
        if (hasChat) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(0.55f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            items(state.messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) Teal.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    ) {
                        Text(
                            text = msg.content.ifEmpty { "…" },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            }
        }

        SnackbarHost(snackbar)

        // --- Eingabeleiste ---
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Schreibe Hermes …") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            val listening = state.orbState == OrbState.LISTENING
            FilledIconButton(
                onClick = {
                    if (listening) speech.stopListening()
                    else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) speech.startListening()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (listening) Violet else Teal
                )
            ) {
                Icon(
                    if (listening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (listening) "Aufnahme stoppen" else "Sprechen"
                )
            }
            FilledIconButton(
                onClick = { vm.send(input); input = "" },
                shape = CircleShape,
                enabled = input.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
            }
        }
    }
}
