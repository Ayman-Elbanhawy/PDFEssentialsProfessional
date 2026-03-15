package com.aymanelbanhawy.aiassistant.core

fun AssistantAudioUiState.beginVoiceCapture(): AssistantAudioUiState = copy(
    enabled = true,
    reason = null,
    voiceCapture = voiceCapture.copy(
        status = VoiceCaptureStatus.Listening,
        transcript = "",
        partialTranscript = "",
        diagnosticsMessage = "Listening for your question...",
        errorMessage = null,
    ),
)

fun AssistantAudioUiState.reduceVoiceCaptureEvent(event: SpeechCaptureEvent): AssistantAudioUiState = when (event) {
    SpeechCaptureEvent.ListeningStarted -> copy(
        voiceCapture = voiceCapture.copy(
            status = VoiceCaptureStatus.Listening,
            diagnosticsMessage = "Listening...",
        ),
    )
    is SpeechCaptureEvent.PartialResult -> copy(
        voiceCapture = voiceCapture.copy(
            status = VoiceCaptureStatus.Recognizing,
            partialTranscript = event.transcript,
            diagnosticsMessage = "Recognizing speech...",
            errorMessage = null,
        ),
    )
    is SpeechCaptureEvent.FinalResult -> copy(
        voiceCapture = voiceCapture.copy(
            status = VoiceCaptureStatus.Completed,
            transcript = event.transcript,
            partialTranscript = "",
            diagnosticsMessage = "Captured voice prompt.",
            errorMessage = null,
        ),
    )
    is SpeechCaptureEvent.Failure -> copy(
        voiceCapture = voiceCapture.copy(
            status = VoiceCaptureStatus.Failed,
            diagnosticsMessage = event.message,
            errorMessage = event.message,
        ),
    )
    SpeechCaptureEvent.Cancelled -> copy(
        voiceCapture = voiceCapture.copy(
            status = VoiceCaptureStatus.Cancelled,
            diagnosticsMessage = "Voice capture cancelled.",
        ),
    )
}

fun AssistantAudioUiState.voiceCaptureStopped(): AssistantAudioUiState = copy(
    voiceCapture = voiceCapture.copy(
        status = VoiceCaptureStatus.Cancelled,
        diagnosticsMessage = "Voice capture stopped.",
    ),
)

fun AssistantAudioUiState.voiceCaptureCancelled(): AssistantAudioUiState = copy(
    voiceCapture = voiceCapture.copy(
        status = VoiceCaptureStatus.Cancelled,
        diagnosticsMessage = "Voice capture cancelled.",
    ),
)

fun AssistantAudioUiState.reduceReadAloudEvent(event: ReadAloudEvent): AssistantAudioUiState = when (event) {
    is ReadAloudEvent.Starting -> copy(
        enabled = true,
        readAloud = readAloud.copy(
            status = ReadAloudStatus.Preparing,
            title = event.title,
            diagnosticsMessage = "Preparing audio...",
            errorMessage = null,
            progress = ReadAloudProgress(totalCount = event.totalSegments),
        ),
    )
    is ReadAloudEvent.SegmentStarted -> copy(
        readAloud = readAloud.copy(
            status = ReadAloudStatus.Speaking,
            diagnosticsMessage = "Reading aloud...",
            progress = ReadAloudProgress(
                currentIndex = event.index,
                totalCount = event.totalSegments,
                currentSegment = event.text,
            ),
        ),
    )
    is ReadAloudEvent.Paused -> copy(
        readAloud = readAloud.copy(
            status = ReadAloudStatus.Paused,
            title = event.title,
            diagnosticsMessage = "Playback paused.",
            errorMessage = null,
            progress = ReadAloudProgress(
                currentIndex = event.index,
                totalCount = event.totalSegments,
                currentSegment = event.text,
            ),
        ),
    )
    is ReadAloudEvent.Completed -> copy(
        readAloud = readAloud.copy(
            status = ReadAloudStatus.Completed,
            title = event.title,
            diagnosticsMessage = "Playback complete.",
            errorMessage = null,
        ),
    )
    is ReadAloudEvent.Failure -> copy(
        readAloud = readAloud.copy(
            status = ReadAloudStatus.Failed,
            diagnosticsMessage = event.message,
            errorMessage = event.message,
        ),
    )
    ReadAloudEvent.Stopped -> copy(
        readAloud = readAloud.copy(
            status = ReadAloudStatus.Stopped,
            diagnosticsMessage = "Playback stopped.",
        ),
    )
}

fun AssistantAudioUiState.readAloudStopped(): AssistantAudioUiState = copy(
    readAloud = readAloud.copy(
        status = ReadAloudStatus.Stopped,
        diagnosticsMessage = "Playback stopped.",
    ),
)

fun AssistantAudioUiState.readAloudPaused(title: String, index: Int, totalSegments: Int, text: String): AssistantAudioUiState {
    return reduceReadAloudEvent(ReadAloudEvent.Paused(title, index, totalSegments, text))
}
