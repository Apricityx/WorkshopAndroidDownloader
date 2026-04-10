package top.apricityx.workshop.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.data.WorkshopRequiredItem

@Composable
fun DownloadDependencyWarningDialog(
    item: WorkshopBrowseItem,
    requiredItems: List<WorkshopRequiredItem>,
    onDismissRequest: () -> Unit,
    onDownloadAllWithDependencies: () -> Unit,
    onDownloadOnlyCurrent: () -> Unit,
) {
    WorkshopDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("还有前置未下载") },
        buttons = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                WorkshopButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDownloadAllWithDependencies,
                ) {
                    Text("下载所有前置并下载模组")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    WorkshopTextButton(onClick = onDismissRequest) {
                        Text("取消")
                    }
                    WorkshopOutlinedButton(onClick = onDownloadOnlyCurrent) {
                        Text("只下载模组")
                    }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("「${item.title}」还有 ${requiredItems.size} 个前置工坊物品未下载。")
            Text("你可以选择下载所有前置后再下载模组，或者只下载当前模组。")
            Text(
                text = requiredItems.joinToString(separator = "\n") { "• ${it.title}" },
            )
        }
    }
}
