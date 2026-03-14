package top.apricityx.workshop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun libraryTabs_areVisibleByDefault() {
        composeRule.onNodeWithTag("gameLibraryTab").assertIsDisplayed()
        composeRule.onNodeWithTag("modLibraryTab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加游戏").assertIsDisplayed()
    }

    @Test
    fun modLibraryDisplayModeToggle_switchesMode() {
        composeRule.onNodeWithTag("modLibraryTab").performClick()
        composeRule.onNodeWithContentDescription("检查模组更新").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切换为总览模式").assertIsDisplayed()
        composeRule.onNodeWithTag("modLibraryDisplayModeToggle").performClick()
        composeRule.onNodeWithContentDescription("切换为大图显示").assertIsDisplayed()
        composeRule.onNodeWithTag("modLibraryDisplayModeToggle").performClick()
        composeRule.onNodeWithContentDescription("切换为精简列表").assertIsDisplayed()
    }
}
