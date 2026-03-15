package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidSpeechCaptureEngine(
    private val context: Context,
) : SpeechCaptureEngine {
    private var speechRecognizer: SpeechRecognizer? = null

    override fun startCapture(): Flow<SpeechCaptureEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechCaptureEvent.Failure("Speech recognition is not available on this device.", retryable = false))
            close()
            return@callbackFlow
        }
        withContext(Dispatchers.Main.immediate) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SpeechCaptureEvent.ListeningStarted)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    val retryable = error in setOf(
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    )
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was cancelled."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice capture."
                        SpeechRecognizer.ERROR_NETWORK -> "Speech recognition network error."
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                        SpeechRecognizer.ERROR_SERVER -> "Speech recognition service failed."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech capture timed out."
                        else -> "Speech recognition failed."
                    }
                    trySend(if (error == SpeechRecognizer.ERROR_CLIENT) SpeechCaptureEvent.Cancelled else SpeechCaptureEvent.Failure(message, retryable))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    trySend(SpeechCaptureEvent.FinalResult(transcript))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (transcript.isNotBlank()) {
                        trySend(SpeechCaptureEvent.PartialResult(transcript))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            recognizer.startListening(intent)
        }
        awaitClose { stopCapture() }
    }

    override fun stopCapture() {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        runCatching { recognizer.stopListening() }
        runCatching { recognizer.cancel() }
        recognizer.destroy()
    }

    override fun cancelCapture() {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        runCatching { recognizer.cancel() }
        recognizer.destroy()
    }
}

class AndroidTextToSpeechReadAloudEngine(
    private val context: Context,
) : ReadAloudEngine {
    private var textToSpeech: TextToSpeech? = null
    private var activeUtteranceIds: List<String> = emptyList()

    override fun speak(request: ReadAloudRequest): Flow<ReadAloudEvent> = callbackFlow {
        val segments = request.text
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(request.text.ifBlank { "No text available." }) }

        withContext(Dispatchers.Main.immediate) {
            val tts = ensureTextToSpeech()
            val ids = segments.map { UUID.randomUUID().toString() }
            activeUtteranceIds = ids
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index = ids.indexOf(utteranceId)
                    if (index >= 0) {
                        trySend(ReadAloudEvent.SegmentStarted(index, segments.size, segments[index]))
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == ids.lastOrNull()) {
                        trySend(ReadAloudEvent.Completed(request.title))
                        close()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    trySend(ReadAloudEvent.Failure("Speech playback failed."))
                    close()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    trySend(ReadAloudEvent.Failure("Speech playback failed with code $errorCode."))
                    close()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (interrupted) {
                        trySend(ReadAloudEvent.Stopped)
                        close()
                    }
                }
            })
            trySend(ReadAloudEvent.Starting(request.title, segments.size))
            segments.forEachIndexed { index, segment ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, ids[index]) }
                tts.speak(segment, queueMode, params, ids[index])
            }
        }

        awaitClose { stop() }
    }

    override fun stop() {
        textToSpeech?.stop()
        activeUtteranceIds = emptyList()
    }

    private suspend fun ensureTextToSpeech(): TextToSpeech = textToSpeech ?: withContext(Dispatchers.Main.immediate) {
        textToSpeech ?: suspendCancellableCoroutine { continuation ->
            var initialized: TextToSpeech? = null
            initialized = TextToSpeech(context.applicationContext) { status ->
                val engine = initialized
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    engine.language = Locale.getDefault()
                    engine.setSpeechRate(1.0f)
                    textToSpeech = engine
                    continuation.resume(engine)
                } else {
                    continuation.cancel(IllegalStateException("Text to speech initialization failed."))
                }
            }
            continuation.invokeOnCancellation {
                initialized?.shutdown()
            }
        }
    }
}
