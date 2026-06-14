package com.homehub.ui.hermes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homehub.core.ServiceLocator
import com.homehub.data.hermes.ChatMessage
import com.homehub.data.hermes.HermesEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiMessage(val role: String, val content: String, val streaming: Boolean = false)

data class HermesUiState(
    val messages: List<UiMessage> = emptyList(),
    val orbState: OrbState = OrbState.IDLE,
    val orbShape: OrbShape = OrbShape.ORB,
    val level: Float = 0f,
    val partialSpeech: String = "",
    val ttsEnabled: Boolean = true,
    val error: String? = null
)

class HermesViewModel : ViewModel() {

    private val _state = MutableStateFlow(HermesUiState())
    val state: StateFlow<HermesUiState> = _state

    private var streamJob: Job? = null
    var speak: ((String) -> Unit)? = null // wird vom Screen mit dem SpeechManager verbunden

    fun onListeningChanged(listening: Boolean) = _state.update {
        it.copy(orbState = if (listening) OrbState.LISTENING else relaxedState(it))
    }

    fun onSpeakingChanged(speaking: Boolean) = _state.update {
        it.copy(orbState = if (speaking) OrbState.SPEAKING else relaxedState(it))
    }

    private fun relaxedState(s: HermesUiState): OrbState =
        if (s.messages.lastOrNull()?.streaming == true) OrbState.THINKING else OrbState.IDLE

    fun onLevel(level: Float) = _state.update { it.copy(level = level) }
    fun onPartial(text: String) = _state.update { it.copy(partialSpeech = text) }
    fun toggleTts() = _state.update { it.copy(ttsEnabled = !it.ttsEnabled) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || streamJob?.isActive == true) return

        // Auch Nutzereingaben können den Orb formen ("erzähl mir was über Schiffe")
        ShapeLibrary.detectShape(content)?.let { s -> _state.update { it.copy(orbShape = s) } }

        _state.update {
            it.copy(
                messages = it.messages + UiMessage("user", content) + UiMessage("assistant", "", streaming = true),
                orbState = OrbState.THINKING,
                partialSpeech = ""
            )
        }

        val history = _state.value.messages
            .filter { !it.streaming }
            .map { ChatMessage(it.role, it.content) }

        streamJob = viewModelScope.launch {
            var full = ""
            ServiceLocator.hermes.streamChat(history).collect { event ->
                when (event) {
                    is HermesEvent.Token -> {
                        full += event.text
                        ShapeLibrary.detectShape(full)?.let { s ->
                            _state.update { it.copy(orbShape = s) }
                        }
                        _state.update {
                            val msgs = it.messages.toMutableList()
                            msgs[msgs.lastIndex] = UiMessage("assistant", full, streaming = true)
                            it.copy(messages = msgs)
                        }
                    }
                    is HermesEvent.Done -> {
                        _state.update {
                            val msgs = it.messages.toMutableList()
                            msgs[msgs.lastIndex] = UiMessage("assistant", full)
                            it.copy(messages = msgs, orbState = OrbState.IDLE)
                        }
                        if (_state.value.ttsEnabled && full.isNotBlank()) speak?.invoke(full)
                    }
                    is HermesEvent.Error -> _state.update {
                        val msgs = it.messages.toMutableList()
                        if (msgs.isNotEmpty() && msgs.last().streaming) msgs.removeAt(msgs.lastIndex)
                        it.copy(messages = msgs, orbState = OrbState.IDLE, error = event.message)
                    }
                }
            }
        }
    }

    fun resetShape() = _state.update { it.copy(orbShape = OrbShape.ORB) }
    fun clearChat() {
        streamJob?.cancel()
        _state.update { HermesUiState(ttsEnabled = it.ttsEnabled) }
    }
}
