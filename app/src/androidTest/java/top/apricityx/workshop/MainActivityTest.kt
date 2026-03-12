package top.apricityx.workshop

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun downloadButton_isDisabledByDefault() {
        composeRule.onNodeWithTag("downloadButton").assertIsNotEnabled()
    }
}
