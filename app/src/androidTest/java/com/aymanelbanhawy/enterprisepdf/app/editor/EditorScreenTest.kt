package com.aymanelbanhawy.enterprisepdf.app.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.filters.SdkSuppress
import com.aymanelbanhawy.enterprisepdf.app.MainActivity
import org.junit.Rule
import org.junit.Test

class EditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun topBarActions_areVisible() {
        composeRule.onNodeWithContentDescription("Annotate").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sign").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open PDF").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").assertIsDisplayed()
    }
}



