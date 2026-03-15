package com.aymanelbanhawy.enterprisepdf.app.quicktools

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.SavedSignatureModel
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.forms.SignatureSourceType
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.aymanelbanhawy.enterprisepdf.app.editor.EditInspectorSidebar
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorUiState
import com.aymanelbanhawy.enterprisepdf.app.forms.FormsSidebar
import com.aymanelbanhawy.enterprisepdf.app.MainActivity
import com.aymanelbanhawy.enterprisepdf.app.organize.OrganizePagesScreen
import com.aymanelbanhawy.enterprisepdf.app.theme.EnterprisePdfTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickToolsInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    @SdkSuppress(maxSdkVersion = 35)
    fun quickPageTools_fireSplitMergeAndRearrangeCallbacks() {
        var splitByRangeCount = 0
        var mergeCount = 0
        var moveEarlierCount = 0
        var moveLaterCount = 0
        var rotateCount = 0

        composeRule.setContent {
            EnterprisePdfTheme {
                OrganizePagesScreen(
                    state = EditorUiState(
                        selectedPageIndexes = setOf(0, 1),
                        splitRangeExpression = "1-2",
                    ),
                    events = emptyFlow(),
                    onBack = {},
                    onSelectPage = {},
                    onMovePage = { _, _ -> },
                    onMoveSelectionBackward = { moveEarlierCount += 1 },
                    onMoveSelectionForward = { moveLaterCount += 1 },
                    onRotateSelected = { rotateCount += 1 },
                    onDeleteSelected = {},
                    onDuplicateSelected = {},
                    onExtractSelected = {},
                    onInsertBlankPage = {},
                    onPickImagePage = {},
                    onPickMergePdfs = { mergeCount += 1 },
                    onUpdateSplitRange = {},
                    onSplitByRange = { splitByRangeCount += 1 },
                    onSplitOddPages = {},
                    onSplitEvenPages = {},
                    onSplitSelectedPages = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Split by Range").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Merge PDFs").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Move Selection Earlier").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Move Selection Later").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Rotate Selected").performSemanticsAction(SemanticsActions.OnClick)

        assertThat(splitByRangeCount).isEqualTo(1)
        assertThat(mergeCount).isEqualTo(1)
        assertThat(moveEarlierCount).isEqualTo(1)
        assertThat(moveLaterCount).isEqualTo(1)
        assertThat(rotateCount).isEqualTo(1)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 35)
    fun lightweightObjectTools_fireTextImageAndSignatureCallbacks() {
        var addTextCount = 0
        var addImageCount = 0
        var captureSignatureCount = 0
        var applySignatureCount = 0

        val selectedTextEdit = TextBoxEditModel(
            id = "text-1",
            pageIndex = 0,
            bounds = NormalizedRect(0.1f, 0.1f, 0.5f, 0.2f),
            text = "Renewal notice",
            fontFamily = FontFamilyToken.Sans,
            fontSizeSp = 18f,
            textColorHex = "#155EEF",
            alignment = TextAlignment.Start,
            lineSpacingMultiplier = 1.3f,
        )
        val imageEdit = ImageEditModel(
            id = "image-1",
            pageIndex = 0,
            bounds = NormalizedRect(0.2f, 0.25f, 0.6f, 0.5f),
            imagePath = "/tmp/quick-tools-image.png",
            label = "Reference image",
        )
        val signatureField = FormFieldModel(
            name = "approver_signature",
            label = "Approver signature",
            pageIndex = 0,
            bounds = NormalizedRect(0.1f, 0.55f, 0.5f, 0.72f),
            type = FormFieldType.Signature,
            value = FormFieldValue.SignatureValue(status = SignatureVerificationStatus.Unsigned),
        )
        val savedSignature = SavedSignatureModel(
            id = "sig-1",
            name = "Jane Approver",
            kind = SignatureKind.Signature,
            imagePath = "/tmp/saved-signature.png",
            createdAtEpochMillis = 1L,
            sourceType = SignatureSourceType.Handwritten,
        )

        composeRule.setContent {
            EnterprisePdfTheme {
                Column {
                    EditInspectorSidebar(
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        editObjects = listOf(selectedTextEdit, imageEdit),
                        selectedEditObject = selectedTextEdit,
                        onSelectEdit = {},
                        onAddTextBox = { addTextCount += 1 },
                        onAddImage = { addImageCount += 1 },
                        onDeleteSelected = {},
                        onDuplicateSelected = {},
                        onReplaceSelectedImage = {},
                        onTextChanged = {},
                        onFontFamilyChanged = {},
                        onFontSizeChanged = {},
                        onTextColorChanged = {},
                        onTextAlignmentChanged = {},
                        onLineSpacingChanged = {},
                        onOpacityChanged = {},
                        onRotationChanged = {},
                    )
                    FormsSidebar(
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        activeSignMode = true,
                        fields = listOf(signatureField),
                        selectedField = signatureField,
                        validationMessage = null,
                        profiles = emptyList(),
                        signatures = listOf(savedSignature),
                        onSelectField = {},
                        onTextChanged = { _, _ -> },
                        onBooleanChanged = { _, _ -> },
                        onChoiceChanged = { _, _ -> },
                        onSaveProfile = {},
                        onApplyProfile = {},
                        onExportFormData = {},
                        onOpenSignatureCapture = { captureSignatureCount += 1 },
                        onApplySignature = { _, _ -> applySignatureCount += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Add Text").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Add Image").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Capture Signature").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Apply saved signature: Jane Approver").performSemanticsAction(SemanticsActions.OnClick)

        assertThat(addTextCount).isEqualTo(1)
        assertThat(addImageCount).isEqualTo(1)
        assertThat(captureSignatureCount).isEqualTo(1)
        assertThat(applySignatureCount).isEqualTo(1)
    }
}
