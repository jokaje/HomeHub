package com.homehub.ui.hermes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Kapselt Spracheingabe (Android SpeechRecognizer, on-device) und
 * Sprachausgabe (Android TextToSpeech). Liefert Pegel & Status an den Orb.
 */
class SpeechManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onSpeakingChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.GERMAN
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { onSpeakingChanged(true) }
                override fun onDone(id: String?) { onSpeakingChanged(false) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { onSpeakingChanged(false) }
            })
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Keine Spracherkennung auf diesem Gerät verfügbar.")
            return
        }
        stopSpeaking()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onListeningChanged(true) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    // rmsdB liegt grob zwischen -2 und 10 -> auf 0..1 normieren
                    onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onListeningChanged(false); onLevel(0f) }
                override fun onError(error: Int) {
                    onListeningChanged(false); onLevel(0f)
                    if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) onError("Spracherkennung fehlgeschlagen (Code $error).")
                }
                override fun onResults(results: Bundle?) {
                    onListeningChanged(false); onLevel(0f)
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let(onFinal)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let(onPartial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        onListeningChanged(false)
        onLevel(0f)
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hermes-${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakingChanged(false)
    }

    fun release() {
        recognizer?.destroy(); recognizer = null
        tts?.shutdown(); tts = null
    }
}
