package top.apricityx.workshop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun dismissUsageNoticeIfShown() {
        if (composeRule.onAllNodesWithText("我知道了").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("我知道了").performClick()
        }
    }

    @Test
    fun libraryTabs_areVisibleByDefault() {
        dismissUsageNoticeIfShown()
        composeRule.onNodeWithTag("gameLibraryTab").assertIsDisplayed()
        composeRule.onNodeWithTag("modLibraryTab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加游戏").assertIsDisplayed()
    }

    @Test
    fun modLibraryDisplayModeToggle_switchesMode() {
        dismissUsageNoticeIfShown()
        composeRule.onNodeWithTag("modLibraryTab").performClick()
        composeRule.onNodeWithContentDescription("检查模组更新").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切换为总览模式").assertIsDisplayed()
        composeRule.onNodeWithTag("modLibraryDisplayModeToggle").performClick()
        composeRule.onNodeWithContentDescription("切换为大图显示").assertIsDisplayed()
        composeRule.onNodeWithTag("modLibraryDisplayModeToggle").performClick()
        composeRule.onNodeWithContentDescription("切换为精简列表").assertIsDisplayed()
    }
}
