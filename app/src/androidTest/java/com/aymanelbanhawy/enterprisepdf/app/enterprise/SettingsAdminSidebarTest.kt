package com.aymanelbanhawy.enterprisepdf.app.enterprise

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorTransferJobModel
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAdminSidebarAiAccessTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ai_toggle_is_disabled_when_plan_lacks_ai_entitlement() {
        val state = EnterpriseAdminStateModel(
            plan = LicensePlan.Premium,
            adminPolicy = AdminPolicyModel(aiEnabled = true),
        )

        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Premium,
            features = setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Security,
            ),
        )

        composeRule.setContent {
            SettingsAdminSidebar(
                modifier = Modifier,
                state = state,
                entitlements = entitlements,
                telemetryEvents = emptyList(),
                diagnosticsCount = 0,
                connectorAccounts = emptyList<ConnectorAccountModel>(),
                connectorJobs = emptyList<ConnectorTransferJobModel>(),
                onSignInPersonal = {},
                onSignInEnterprise = { _, _ -> },
                onSignOut = {},
                onSetPlan = {},
                onUpdatePrivacy = {},
                onUpdatePolicy = {},
                onGenerateDiagnostics = {},
                onRefreshRemoteState = {},
                onFlushTelemetry = {},
                onSaveConnectorAccount = {},
                onTestConnectorConnection = {},
                onOpenConnectorDocument = { _, _, _ -> },
                onSyncConnectorTransfers = {},
                onCleanupConnectorCache = {},
            )
        }

        composeRule
            .onNodeWithTag("settings-admin-ai-enabled-switch")
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag("settings-admin-ai-enabled-message")
            .assertTextContains("AI is not included in the current plan")
    }
}