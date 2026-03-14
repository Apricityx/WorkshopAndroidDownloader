package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.BaiduTranslationApiKeyUiState
import top.apricityx.workshop.R
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun BaiduTranslationApiKeyScreen(
    state: BaiduTranslationApiKeyUiState,
    onAppIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestTranslation: () -> Unit,
    onOpenApiKeyGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkshopPanelCard {
            Text("获取教程", style = MaterialTheme.typography.titleLarge)
            Text(
                "1. 打开百度翻译开放平台凭据管理页。\n2. 登录后记录大模型文本翻译对应的 AppID 和 API Key。\n3. 返回此页面填入 AppID 与 API Key 并保存。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenApiKeyGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往管理凭据")
            }
        }

        WorkshopPanelCard {
            Text("百度大模型文本翻译凭据", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (state.hasSavedCredentials) {
                    "当前已经保存过完整凭据。修改后点保存会覆盖原值；清空后保存可移除本地配置。"
                } else {
                    "需要同时填写百度大模型文本翻译的 AppID 和 API Key。留空后保存会清除对应本地配置。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.appIdInput,
                onValueChange = onAppIdChange,
                label = { Text("AppID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }

            OutlinedButton(
                onClick = onTestTranslation,
                enabled = !state.isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("正在测试翻译…")
                } else {
                    Text("测试翻译")
                }
            }
        }

        WorkshopPanelCard {
            Text("示例文本", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.sampleSourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            state.testResultText?.let { testResultText ->
                Text("测试结果", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = testResultText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            state.testFailureReason?.let { testFailureReason ->
                Text(
                    text = "失败原因",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = testFailureReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        state.message?.let { message ->
            WorkshopMessageBanner(
                message = message,
                tone = MessageTone.Success,
            )
        }

        BaiduTranslationTutorialCard(
            onOpenApiKeyGuide = onOpenApiKeyGuide,
        )
    }
}

@Composable
private fun BaiduTranslationTutorialCard(
    onOpenApiKeyGuide: () -> Unit,
) {
    WorkshopPanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("内置教程", style = MaterialTheme.typography.titleLarge)
            Text(
                "下面这套图文步骤来自项目里的教程稿，可以直接按顺序操作完成百度大模型文本翻译开通。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenApiKeyGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开百度翻译开放平台")
            }

            baiduTutorialSteps.forEachIndexed { index, step ->
                BaiduTranslationTutorialStep(
                    stepNumber = index + 1,
                    step = step,
                    topPadding = if (index == 0) 0.dp else 4.dp,
                )
            }
        }
    }
}

@Composable
private fun BaiduTranslationTutorialStep(
    stepNumber: Int,
    step: BaiduTutorialStep,
    topPadding: Dp,
) {
    Column(
        modifier = Modifier.padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "$stepNumber. ${step.text}",
            style = MaterialTheme.typography.titleMedium,
        )

        step.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        step.imageResId?.let { imageResId ->
            Image(
                painter = painterResource(id = imageResId),
                contentDescription = step.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

private data class BaiduTutorialStep(
    val text: String,
    val imageResId: Int? = null,
    val note: String? = null,
)

private val baiduTutorialSteps = listOf(
    BaiduTutorialStep(
        text = "进入百度翻译开放平台。",
        note = "入口地址：`https://fanyi-api.baidu.com/product/13`",
    ),
    BaiduTutorialStep(
        text = "点击“立即使用”。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_02,
    ),
    BaiduTutorialStep(
        text = "根据提示填写个人信息。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_03,
    ),
    BaiduTutorialStep(
        text = "完成实名认证。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_04,
    ),
    BaiduTutorialStep(
        text = "认证完成后回到控制台，点击“立即开通”。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_05,
    ),
    BaiduTutorialStep(
        text = "选择“大模型文本翻译”。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_06,
    ),
    BaiduTutorialStep(
        text = "随便填一点测试内容后提交申请。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_07,
    ),
    BaiduTutorialStep(
        text = "回到主界面，点击“开发者信息”。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_08,
    ),
    BaiduTutorialStep(
        text = "把 AppID 记下来。",
    ),
    BaiduTutorialStep(
        text = "进入“API Key 管理”，创建一个新的 API Key，名称可以随便填。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_10,
    ),
    BaiduTutorialStep(
        text = "把新建出来的 API Key 也记下来。",
        imageResId = R.drawable.baidu_ai_text_tutorial_step_10_result,
    ),
    BaiduTutorialStep(
        text = "回到 App，把 AppID 和 API Key 填进上面的配置区后保存即可使用。",
    ),
)
