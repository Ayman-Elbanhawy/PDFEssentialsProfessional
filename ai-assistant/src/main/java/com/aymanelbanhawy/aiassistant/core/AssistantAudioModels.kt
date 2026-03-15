package com.aymanelbanhawy.aiassistant.core

import kotlinx.serialization.Serializable

@Serializable
enum class VoiceCaptureStatus {
    Idle,
    Listening,
    Recognizing,
    Completed,
    Failed,
    Cancelled,
}

@Serializable
enum class ReadAloudStatus {
    Idle,
    Preparing,
    Speaking,
    Paused,
    Completed,
    Failed,
    Stopped,
}

@Serializable
data class VoiceCaptureUiState(
    val status: VoiceCaptureStatus = VoiceCaptureStatus.Idle,
    val transcript: String = "",
    val partialTranscript: String = "",
    val diagnosticsMessage: String = "",
    val errorMessage: String? = null,
)

@Serializable
data class ReadAloudProgress(
    val currentIndex: Int = -1,
    val totalCount: Int = 0,
    val currentSegment: String = "",
)

@Serializable
data class ReadAloudUiState(
    val status: ReadAloudStatus = ReadAloudStatus.Idle,
    val title: String = "",
    val diagnosticsMessage: String = "",
    val errorMessage: String? = null,
    val progress: ReadAloudProgress = ReadAloudProgress(),
)

@Serializable
data class AssistantAudioUiState(
    val enabled: Boolean = false,
    val reason: String? = null,
    val voiceCapture: VoiceCaptureUiState = VoiceCaptureUiState(),
    val readAloud: ReadAloudUiState = ReadAloudUiState(),
)

data class SpeechCaptureResult(
    val transcript: String,
    val partialTranscript: String = "",
)

sealed interface SpeechCaptureEvent {
    data object ListeningStarted : SpeechCaptureEvent
    data class PartialResult(val transcript: String) : SpeechCaptureEvent
    data class FinalResult(val transcript: String) : SpeechCaptureEvent
    data class Failure(val message: String, val retryable: Boolean) : SpeechCaptureEvent
    data object Cancelled : SpeechCaptureEvent
}

sealed interface ReadAloudEvent {
    data class Starting(val title: String, val totalSegments: Int) : ReadAloudEvent
    data class SegmentStarted(val index: Int, val totalSegments: Int, val text: String) : ReadAloudEvent
    data class Paused(val title: String, val index: Int, val totalSegments: Int, val text: String) : ReadAloudEvent
    data class Completed(val title: String) : ReadAloudEvent
    data class Failure(val message: String) : ReadAloudEvent
    data object Stopped : ReadAloudEvent
}

data class ReadAloudRequest(
    val title: String,
    val text: String,
)

interface SpeechCaptureEngine {
    fun startCapture(): kotlinx.coroutines.flow.Flow<SpeechCaptureEvent>
    fun stopCapture()
    fun cancelCapture()
}

interface ReadAloudEngine {
    fun speak(request: ReadAloudRequest): kotlinx.coroutines.flow.Flow<ReadAloudEvent>
    fun stop()
}
