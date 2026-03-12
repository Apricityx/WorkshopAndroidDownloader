package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.LibraryErrorUiState
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.ui.component.GameShowcaseCard
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner

@Composable
fun LibraryScreen(
    games: List<SteamGame>,
    isLoading: Boolean,
    message: String?,
    error: LibraryErrorUiState?,
    onRetry: () -> Unit,
    onOpenGame: (SteamGame) -> Unit,
    onRemoveGame: (SteamGame) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && games.isEmpty() -> WorkshopLoadingBlock(
            label = "",
            modifier = modifier.padding(top = 24.dp),
        )

        error != null && games.isEmpty() -> WorkshopCenteredState(
            title = "游戏库加载失败",
            message = buildString {
                append(error.reason)
                if (error.showAcceleratorHint) {
                    append("\n\n啊哦，您的网络环境貌似不支持直连创意工坊，尝试开启科学上网或者开启加速器后重试。")
                }
            },
            actionLabel = "重试",
            onAction = onRetry,
            modifier = modifier.padding(top = 24.dp),
        )

        games.isEmpty() -> WorkshopCenteredState(
            title = "游戏库还是空的",
            message = message ?: "点右上角 + 添加支持创意工坊的游戏。",
            modifier = modifier.padding(top = 24.dp),
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            if (isLoading) {
                item {
                    WorkshopMessageBanner(
                        message = "正在刷新游戏库",
                        tone = MessageTone.Info,
                    )
                }
            }

            message?.let { info ->
                item {
                    WorkshopMessageBanner(
                        message = info,
                        tone = MessageTone.Info,
                    )
                }
            }

            item {
                SectionHeading(
                    title = "已添加的游戏",
                    subtitle = "点击“查看工坊”进入该游戏的创意工坊列表。",
                )
            }

            items(games, key = { it.appId.toString() }) { game ->
                GameShowcaseCard(
                    game = game,
                    primaryActionLabel = "查看工坊",
                    onPrimaryAction = { onOpenGame(game) },
                    secondaryActionLabel = "移出游戏库",
                    onSecondaryAction = { onRemoveGame(game) },
                )
            }
        }
    }
}
