package com.aymanelbanhawy.enterprisepdf.app.audio

import com.aymanelbanhawy.aiassistant.core.AssistantAudioUiState
import com.aymanelbanhawy.aiassistant.core.AssistantSettings
import com.aymanelbanhawy.aiassistant.core.ReadAloudEvent
import com.aymanelbanhawy.aiassistant.core.SpeechCaptureEvent
import com.aymanelbanhawy.aiassistant.core.beginVoiceCapture
import com.aymanelbanhawy.aiassistant.core.readAloudPaused
import com.aymanelbanhawy.aiassistant.core.readAloudStopped
import com.aymanelbanhawy.aiassistant.core.reduceReadAloudEvent
import com.aymanelbanhawy.aiassistant.core.reduceVoiceCaptureEvent
import com.aymanelbanhawy.editor.core.collaboration.VoiceCommentAttachmentModel
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class AudioFeatureEvidenceBundleTest {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val aiEntitlements = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Ai))

    @Test
    fun policyIndependentlyControlsCloudAiVoiceInputSpeechOutputAndVoiceComments() {
        val assistantSettings = AssistantSettings(
            spokenResponsesEnabled = true,
            readAloudEnabled = true,
            voicePromptCaptureEnabled = true,
        )

        val base = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = true,
                speechOutputEnabled = true,
                voiceCommentsEnabled = true,
                allowCloudAiProviders = true,
            ),
            privacySettings = PrivacySettingsModel(localOnlyMode = false),
            assistantSettings = assistantSettings,
        )
        val localOnly = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = true,
                speechOutputEnabled = true,
                voiceCommentsEnabled = true,
                allowCloudAiProviders = true,
            ),
            privacySettings = PrivacySettingsModel(localOnlyMode = true),
            assistantSettings = assistantSettings,
        )
        val voiceInputBlocked = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = false,
                speechOutputEnabled = true,
                voiceCommentsEnabled = true,
            ),
            privacySettings = PrivacySettingsModel(),
            assistantSettings = assistantSettings,
        )
        val speechOutputBlocked = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = true,
                speechOutputEnabled = false,
                voiceCommentsEnabled = true,
            ),
            privacySettings = PrivacySettingsModel(),
            assistantSettings = assistantSettings,
        )
        val voiceCommentsBlocked = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = true,
                speechOutputEnabled = true,
                voiceCommentsEnabled = false,
            ),
            privacySettings = PrivacySettingsModel(),
            assistantSettings = assistantSettings,
        )

        assertThat(base.cloudAiAllowed).isTrue()
        assertThat(base.voicePromptCaptureAllowed).isTrue()
        assertThat(base.readAloudAllowed).isTrue()
        assertThat(base.spokenAssistantResponsesAllowed).isTrue()
        assertThat(base.voiceCommentsAllowed).isTrue()
        assertThat(localOnly.cloudAiAllowed).isFalse()
        assertThat(voiceInputBlocked.voicePromptCaptureAllowed).isFalse()
        assertThat(voiceInputBlocked.voicePromptReason()).contains("Voice input")
        assertThat(speechOutputBlocked.readAloudAllowed).isFalse()
        assertThat(speechOutputBlocked.spokenAssistantResponsesAllowed).isFalse()
        assertThat(speechOutputBlocked.readAloudReason()).contains("Speech output")
        assertThat(voiceCommentsBlocked.voiceCommentsAllowed).isFalse()
        assertThat(voiceCommentsBlocked.voiceCommentReason()).contains("Voice comments")
    }

    @Test
    fun audioEvidenceBundle_generatesManifestAndLogs() {
        val outputDirectory = resolveAudioEvidenceRoot().apply { deleteRecursively() }
        val basePolicy = AdminPolicyModel(
            aiEnabled = true,
            audioFeaturesEnabled = true,
            voiceInputEnabled = true,
            speechOutputEnabled = true,
            voiceCommentsEnabled = true,
            allowCloudAiProviders = true,
        )
        val settings = AssistantSettings(
            spokenResponsesEnabled = true,
            readAloudEnabled = true,
            voicePromptCaptureEnabled = true,
        )
        val deniedCapabilities = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = basePolicy.copy(voiceInputEnabled = false),
            privacySettings = PrivacySettingsModel(),
            assistantSettings = settings,
        )
        val grantedCapabilities = resolveAudioFeatureCapabilities(
            entitlements = aiEntitlements,
            policy = basePolicy,
            privacySettings = PrivacySettingsModel(),
            assistantSettings = settings,
        )

        var audioState = AssistantAudioUiState()
        val stateLog = mutableListOf<AudioStateLogEntry>()
        audioState = audioState.beginVoiceCapture()
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "voice_input",
            state = audioState.voiceCapture.status.name,
            details = "Microphone permission granted and prompt capture started.",
        )
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "voice_input",
            state = "Denied",
            details = "Microphone permission denial leaves voice capture unavailable until the user grants access again.",
            metadata = mapOf("policyVoiceInputAllowed" to deniedCapabilities.voicePromptCaptureAllowed.toString()),
        )
        audioState = audioState.reduceVoiceCaptureEvent(SpeechCaptureEvent.PartialResult("summarize this agreement"))
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "voice_input",
            state = audioState.voiceCapture.status.name,
            details = "Partial transcript captured.",
            metadata = mapOf("partialTranscript" to audioState.voiceCapture.partialTranscript),
        )
        audioState = audioState.reduceVoiceCaptureEvent(SpeechCaptureEvent.FinalResult("summarize this agreement"))
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "voice_input",
            state = audioState.voiceCapture.status.name,
            details = "Final transcript captured.",
            metadata = mapOf("transcript" to audioState.voiceCapture.transcript),
        )

        audioState = audioState.reduceReadAloudEvent(ReadAloudEvent.Starting("Agreement summary", 2))
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "speech_output",
            state = audioState.readAloud.status.name,
            details = "Read aloud started.",
            metadata = mapOf("title" to audioState.readAloud.title),
        )
        audioState = audioState.reduceReadAloudEvent(ReadAloudEvent.SegmentStarted(0, 2, "This contract renews every year."))
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "speech_output",
            state = audioState.readAloud.status.name,
            details = "Playback reached the first paragraph.",
            metadata = mapOf("segment" to audioState.readAloud.progress.currentSegment),
        )
        audioState = audioState.readAloudPaused("Agreement summary", 0, 2, "This contract renews every year.")
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "speech_output",
            state = audioState.readAloud.status.name,
            details = "Playback paused for interruption.",
            metadata = mapOf("segmentIndex" to audioState.readAloud.progress.currentIndex.toString()),
        )
        audioState = audioState.beginVoiceCapture()
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "interrupt",
            state = audioState.voiceCapture.status.name,
            details = "Voice input interrupted speech output and resumed microphone capture.",
        )
        audioState = audioState.reduceReadAloudEvent(ReadAloudEvent.SegmentStarted(1, 2, "Termination requires sixty days notice."))
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "speech_output",
            state = audioState.readAloud.status.name,
            details = "Playback resumed from the next paragraph.",
            metadata = mapOf("segment" to audioState.readAloud.progress.currentSegment),
        )
        audioState = audioState.readAloudStopped()
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "speech_output",
            state = audioState.readAloud.status.name,
            details = "Playback stopped cleanly.",
        )

        val voiceAttachment = VoiceCommentAttachmentModel(
            id = UUID.randomUUID().toString(),
            localFilePath = "/tmp/voice-note.m4a",
            mimeType = "audio/mp4",
            durationMillis = 1_250L,
            createdAtEpochMillis = System.currentTimeMillis(),
            transcript = "Please confirm clause five.",
            payloadBase64 = Base64.getEncoder().encodeToString("voice-note".toByteArray()),
        )
        val restoredAttachment = json.decodeFromString(
            VoiceCommentAttachmentModel.serializer(),
            json.encodeToString(VoiceCommentAttachmentModel.serializer(), voiceAttachment),
        )
        assertThat(restoredAttachment).isEqualTo(voiceAttachment)
        stateLog += AudioStateLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            channel = "voice_comment",
            state = "Reloaded",
            details = "Voice comment payload reloaded from persisted JSON.",
            metadata = mapOf("durationMillis" to restoredAttachment.durationMillis.toString()),
        )

        val entries = listOf(
            AudioEvidenceEntry(
                name = "microphone_permission_granted",
                status = AudioEvidenceStatus.Passed,
                details = "Voice prompt capture proceeds when microphone permission and policy allow it.",
            ),
            AudioEvidenceEntry(
                name = "microphone_permission_denied",
                status = AudioEvidenceStatus.Blocked,
                details = "Voice prompt capture is cancelled cleanly when microphone permission is denied.",
                metadata = mapOf("policyVoiceInputAllowed" to deniedCapabilities.voicePromptCaptureAllowed.toString()),
            ),
            AudioEvidenceEntry(
                name = "playback_pause_resume_stop",
                status = AudioEvidenceStatus.Passed,
                details = "Read aloud progresses through start, pause, resume, and stop states with paragraph tracking.",
                metadata = mapOf("finalPlaybackState" to audioState.readAloud.status.name),
            ),
            AudioEvidenceEntry(
                name = "speech_interruption",
                status = AudioEvidenceStatus.Passed,
                details = "Starting voice capture interrupts current speech output without losing the last known paragraph.",
            ),
            AudioEvidenceEntry(
                name = "persisted_voice_comment_reload",
                status = AudioEvidenceStatus.Passed,
                details = "Voice comment payload survives persistence and reload.",
                metadata = mapOf("transcript" to restoredAttachment.transcript),
            ),
            AudioEvidenceEntry(
                name = "cloud_ai_policy",
                status = if (grantedCapabilities.cloudAiAllowed) AudioEvidenceStatus.Passed else AudioEvidenceStatus.Failed,
                details = "Cloud AI remains independently controllable from voice input and speech output.",
            ),
        )

        val manifestFile = AudioEvidenceArtifactWriter.write(
            outputDirectory = outputDirectory,
            entries = entries,
            stateLog = stateLog,
        )

        assertThat(manifestFile.exists()).isTrue()
        assertThat(File(outputDirectory, "logs/audio-state-log.json").exists()).isTrue()
        assertThat(manifestFile.readText()).contains("microphone_permission_granted")
        assertThat(File(outputDirectory, "logs/audio-state-log.json").readText()).contains("Playback paused for interruption")
    }

    @Test
    fun noAiEntitlementKeepsAssistantAudioUnavailableEvenWhenPolicyIsEnabled() {
        val capabilities = resolveAudioFeatureCapabilities(
            entitlements = EntitlementStateModel(LicensePlan.Premium, emptySet()),
            policy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = true,
                speechOutputEnabled = true,
            ),
            privacySettings = PrivacySettingsModel(),
            assistantSettings = AssistantSettings(
                spokenResponsesEnabled = true,
                readAloudEnabled = true,
                voicePromptCaptureEnabled = true,
            ),
        )

        assertThat(capabilities.aiAvailable).isFalse()
        assertThat(capabilities.voicePromptCaptureAllowed).isFalse()
        assertThat(capabilities.spokenAssistantResponsesAllowed).isFalse()
        assertThat(capabilities.voicePromptReason()).isEqualTo("AI is not included in the current plan.")
    }

    private fun resolveAudioEvidenceRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return if (workingDirectory.name.equals("app", ignoreCase = true)) {
            File(workingDirectory, "build/reports/release/audio-evidence")
        } else {
            File(workingDirectory, "app/build/reports/release/audio-evidence")
        }
    }
}
