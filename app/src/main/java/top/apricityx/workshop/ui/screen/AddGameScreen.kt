package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.AddGameUiState
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.ui.component.GameShowcaseCard
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun AddGameScreen(
    state: AddGameUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDirectAppIdChange: (String) -> Unit,
    onAddById: () -> Unit,
    onAddGame: (SteamGame) -> Unit,
    onOpenGame: (SteamGame) -> Unit,
    onRetryFeaturedLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenSummaryCard(
                title = "添加游戏",
                subtitle = "你可以搜索游戏名、直接输入 GameID，或者从热门创意工坊游戏里快速加入。",
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            WorkshopPanelCard {
                Text(
                    text = "搜索游戏",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("搜索游戏") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) {
                        Text("搜索")
                    }
                }
            }
        }

        item {
            WorkshopPanelCard {
                Text(
                    text = "直接填写 GameID",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.directAppIdText,
                        onValueChange = onDirectAppIdChange,
                        label = { Text("直接填写 GameID") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onAddById, modifier = Modifier.padding(top = 8.dp)) {
                        Text("添加")
                    }
                }
            }
        }

        state.message?.let { message ->
            item {
                WorkshopMessageBanner(
                    message = message,
                    tone = if (message.contains("失败") || message.contains("超时")) MessageTone.Error else MessageTone.Info,
                )
            }
        }

        if (state.isSearching) {
            item {
                WorkshopLoadingBlock(label = "正在搜索支持创意工坊的游戏。")
            }
        }

        if (state.searchResults.isNotEmpty()) {
            item {
                SectionHeading(
                    title = "搜索结果",
                    subtitle = "找到后可以直接加入游戏库，或者马上打开它的创意工坊。",
                )
            }

            items(state.searchResults, key = { "search-${it.appId}" }) { game ->
                GameShowcaseCard(
                    game = game,
                    primaryActionLabel = "加入游戏库",
                    onPrimaryAction = { onAddGame(game) },
                    secondaryActionLabel = "直接打开",
                    onSecondaryAction = { onOpenGame(game) },
                )
            }
        } else if (state.searchQuery.isNotBlank() && !state.isSearching && !state.searchRequestFailed) {
            item {
                WorkshopCenteredState(
                    title = "没有找到结果",
                    message = "试试更短的关键字，或者直接填写游戏的 AppID。",
                )
            }
        }

        item {
            SectionHeading(
                title = "热门创意工坊游戏",
            )
        }

        if (state.isLoadingFeatured) {
            item {
                WorkshopLoadingBlock(label = "正在加载热门创意工坊游戏。")
            }
        } else if (state.featuredGames.isEmpty() && !state.featuredErrorMessage.isNullOrBlank()) {
            item {
                WorkshopCenteredState(
                    title = "加载失败",
                    message = state.featuredErrorMessage,
                    actionLabel = "重试",
                    onAction = onRetryFeaturedLoad,
                )
            }
        } else if (state.featuredGames.isEmpty()) {
            item {
                WorkshopCenteredState(
                    title = "暂时没有热门推荐",
                    message = "稍后重试，或者直接使用搜索 / GameID 添加。",
                )
            }
        } else {
            items(state.featuredGames, key = { "featured-${it.appId}" }) { game ->
                GameShowcaseCard(
                    game = game,
                    primaryActionLabel = "加入游戏库",
                    onPrimaryAction = { onAddGame(game) },
                    secondaryActionLabel = "直接打开",
                    onSecondaryAction = { onOpenGame(game) },
                )
            }
        }

        item {
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
    }
}
