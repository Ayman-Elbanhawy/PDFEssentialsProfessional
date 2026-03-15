package com.aymanelbanhawy.enterprisepdf.app.audio

import com.aymanelbanhawy.aiassistant.core.AssistantSettings
import com.aymanelbanhawy.editor.core.enterprise.AiFeatureAccessResolution
import com.aymanelbanhawy.editor.core.enterprise.AiFeatureAccessResolver
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel

data class AudioFeatureCapabilities(
    val aiEntitled: Boolean,
    val aiPolicyEnabled: Boolean,
    val aiAvailable: Boolean,
    val cloudAiAllowed: Boolean,
    val speechOutputPolicyEnabled: Boolean,
    val voicePromptCaptureAllowed: Boolean,
    val readAloudAllowed: Boolean,
    val spokenAssistantResponsesAllowed: Boolean,
    val voiceCommentsAllowed: Boolean,
) {
    fun assistantAudioEnabled(): Boolean {
        return voicePromptCaptureAllowed || readAloudAllowed || spokenAssistantResponsesAllowed
    }

    fun assistantAudioReason(): String? {
        return when {
            aiAvailable && assistantAudioEnabled() -> null
            !aiEntitled -> AiFeatureAccessResolution.ENTITLEMENT_REQUIRED_MESSAGE
            !aiPolicyEnabled -> AiFeatureAccessResolution.POLICY_DISABLED_MESSAGE
            !voicePromptCaptureAllowed && !readAloudAllowed && !spokenAssistantResponsesAllowed -> "Voice input and speech output are disabled by enterprise policy."
            else -> "Audio features are restricted by enterprise policy."
        }
    }

    fun voicePromptReason(): String? {
        return when {
            voicePromptCaptureAllowed -> null
            !aiEntitled -> AiFeatureAccessResolution.ENTITLEMENT_REQUIRED_MESSAGE
            !aiPolicyEnabled -> AiFeatureAccessResolution.POLICY_DISABLED_MESSAGE
            else -> "Voice input is disabled by enterprise policy."
        }
    }

    fun readAloudReason(): String? {
        return when {
            readAloudAllowed -> null
            !speechOutputPolicyEnabled -> "Speech output is disabled by enterprise policy."
            else -> "Read aloud is disabled in assistant settings."
        }
    }

    fun spokenResponseReason(): String? {
        return when {
            spokenAssistantResponsesAllowed -> null
            !speechOutputPolicyEnabled -> "Speech output is disabled by enterprise policy."
            else -> "Spoken assistant responses are disabled in assistant settings."
        }
    }

    fun voiceCommentReason(): String? {
        return if (voiceCommentsAllowed) null else "Voice comments are disabled by enterprise policy."
    }
}

fun resolveAudioFeatureCapabilities(
    entitlements: EntitlementStateModel,
    policy: AdminPolicyModel,
    privacySettings: PrivacySettingsModel,
    assistantSettings: AssistantSettings,
): AudioFeatureCapabilities {
    val aiAccess = AiFeatureAccessResolver.resolve(entitlements, policy)
    val aiAvailable = aiAccess.available
    val audioAvailable = policy.audioFeaturesEnabled
    val cloudAiAllowed = aiAvailable && policy.allowCloudAiProviders && !privacySettings.localOnlyMode
    val voicePromptCaptureAllowed = aiAvailable && audioAvailable && policy.voiceInputEnabled && assistantSettings.voicePromptCaptureEnabled
    val speechOutputAllowed = audioAvailable && policy.speechOutputEnabled
    val readAloudAllowed = speechOutputAllowed && assistantSettings.readAloudEnabled
    val spokenAssistantResponsesAllowed = aiAvailable && speechOutputAllowed && assistantSettings.spokenResponsesEnabled
    val voiceCommentsAllowed = audioAvailable && policy.voiceCommentsEnabled
    return AudioFeatureCapabilities(
        aiEntitled = aiAccess.entitled,
        aiPolicyEnabled = aiAccess.policyEnabled,
        aiAvailable = aiAvailable,
        cloudAiAllowed = cloudAiAllowed,
        speechOutputPolicyEnabled = speechOutputAllowed,
        voicePromptCaptureAllowed = voicePromptCaptureAllowed,
        readAloudAllowed = readAloudAllowed,
        spokenAssistantResponsesAllowed = spokenAssistantResponsesAllowed,
        voiceCommentsAllowed = voiceCommentsAllowed,
    )
}
