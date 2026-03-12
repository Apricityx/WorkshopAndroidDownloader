package top.apricityx.workshop.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.apricityx.workshop.data.SteamGame

@Composable
fun GameShowcaseCard(
    game: SteamGame,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkshopPanelCard(modifier = modifier) {
        AsyncImage(
            model = game.headerImageUrl.ifBlank { game.capsuleImageUrl },
            contentDescription = game.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(460f / 215f),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            MetricFlow(metrics = listOf("AppID ${game.appId}"))
            Text(
                text = game.shortDescription.ifBlank { "这个游戏支持 Steam 创意工坊。" },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f),
            ) {
                Text(primaryActionLabel)
            }

            OutlinedButton(
                onClick = onSecondaryAction,
                modifier = Modifier.weight(1f),
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
}
