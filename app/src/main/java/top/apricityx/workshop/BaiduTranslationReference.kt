package top.apricityx.workshop

private const val BAIDU_REFERENCE_LIMIT = 500
private const val BAIDU_REFERENCE_NAME_LIMIT = 80
private val BAIDU_REFERENCE_WHITESPACE = Regex("\\s+")

internal fun buildBaiduModDescriptionReference(
    modTitle: String,
    gameTitle: String,
): String =
    buildString {
        append("将内容视为 Steam 创意工坊模组说明进行翻译。")

        normalizeBaiduReferenceValue(modTitle).takeIf(String::isNotEmpty)?.let { normalizedModTitle ->
            append("模组名：")
            append(normalizedModTitle)
            append('。')
        }

        normalizeBaiduReferenceValue(gameTitle).takeIf(String::isNotEmpty)?.let { normalizedGameTitle ->
            append("所属游戏：")
            append(normalizedGameTitle)
            append('。')
        }

        append(
            "请结合该游戏和模组语境，保持自然准确；优先保留游戏名、模组名、专有术语、角色名、道具名、技能名、版本号、文件路径、代码、链接和原有格式，不要补充原文没有的信息。",
        )
    }.take(BAIDU_REFERENCE_LIMIT)

private fun normalizeBaiduReferenceValue(value: String): String =
    value.trim()
        .replace(BAIDU_REFERENCE_WHITESPACE, " ")
        .take(BAIDU_REFERENCE_NAME_LIMIT)
