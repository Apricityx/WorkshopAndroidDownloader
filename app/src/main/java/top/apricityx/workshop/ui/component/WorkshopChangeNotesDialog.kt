package top.apricityx.workshop.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WorkshopChangeNotesDialog(
    title: String,
    markdown: String,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetryRequest: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onOpenExternalUrl: (() -> Unit)? = null,
) {
    WorkshopDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        buttons = {
            WorkshopOutlinedButton(onClick = onDismissRequest) {
                Text("关闭")
            }
            onOpenExternalUrl?.let { openExternalUrl ->
                WorkshopButton(
                    onClick = {
                        onDismissRequest()
                        openExternalUrl()
                    },
                ) {
                    Text("在 Steam 中打开")
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                isLoading && markdown.isBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "正在加载更新日志…",
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                markdown.isNotBlank() -> {
                    SimpleMarkdownCard(
                        title = "更新日志",
                        markdown = markdown,
                    )
                }

                else -> {
                    errorMessage?.let { message ->
                        WorkshopMessageBanner(
                            message = message,
                            tone = MessageTone.Error,
                        )
                    }
                    Text(
                        text = "暂无更新日志。",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    if (errorMessage != null) {
                        onRetryRequest?.let { onRetry ->
                        WorkshopOutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("重试")
                        }
                    }
                    }
                }
            }
        }
    }
}
