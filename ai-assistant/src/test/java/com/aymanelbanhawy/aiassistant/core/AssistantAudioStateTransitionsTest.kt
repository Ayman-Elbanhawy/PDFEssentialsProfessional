package com.aymanelbanhawy.aiassistant.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AssistantAudioStateTransitionsTest {
    @Test
    fun voiceCaptureTransitions_updateExpectedState() {
        val started = AssistantAudioUiState().beginVoiceCapture()
        val partial = started.reduceVoiceCaptureEvent(SpeechCaptureEvent.PartialResult("summarize the contract"))
        val completed = partial.reduceVoiceCaptureEvent(SpeechCaptureEvent.FinalResult("summarize the contract"))

        assertThat(started.voiceCapture.status).isEqualTo(VoiceCaptureStatus.Listening)
        assertThat(partial.voiceCapture.status).isEqualTo(VoiceCaptureStatus.Recognizing)
        assertThat(partial.voiceCapture.partialTranscript).isEqualTo("summarize the contract")
        assertThat(completed.voiceCapture.status).isEqualTo(VoiceCaptureStatus.Completed)
        assertThat(completed.voiceCapture.transcript).isEqualTo("summarize the contract")
        assertThat(completed.voiceCapture.partialTranscript).isEmpty()
    }

    @Test
    fun readAloudTransitions_trackProgressAndStopState() {
        val preparing = AssistantAudioUiState().reduceReadAloudEvent(ReadAloudEvent.Starting("Page 1", 3))
        val speaking = preparing.reduceReadAloudEvent(ReadAloudEvent.SegmentStarted(1, 3, "Paragraph 2"))
        val paused = speaking.readAloudPaused("Page 1", 1, 3, "Paragraph 2")
        val stopped = paused.readAloudStopped()

        assertThat(preparing.readAloud.status).isEqualTo(ReadAloudStatus.Preparing)
        assertThat(preparing.readAloud.progress.totalCount).isEqualTo(3)
        assertThat(speaking.readAloud.status).isEqualTo(ReadAloudStatus.Speaking)
        assertThat(speaking.readAloud.progress.currentIndex).isEqualTo(1)
        assertThat(speaking.readAloud.progress.currentSegment).isEqualTo("Paragraph 2")
        assertThat(paused.readAloud.status).isEqualTo(ReadAloudStatus.Paused)
        assertThat(paused.readAloud.progress.currentSegment).isEqualTo("Paragraph 2")
        assertThat(stopped.readAloud.status).isEqualTo(ReadAloudStatus.Stopped)
        assertThat(stopped.readAloud.diagnosticsMessage).isEqualTo("Playback stopped.")
    }
}
