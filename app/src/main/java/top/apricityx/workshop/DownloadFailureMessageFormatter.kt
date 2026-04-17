package top.apricityx.workshop

private const val STEAM_CDN_UNAUTHORIZED_MESSAGE = "Steam CDN request failed: 401"

internal fun formatDownloadFailureMessage(
    rawMessage: String,
    gameTitle: String,
    hasBoundAccount: Boolean,
    ownershipStatus: SteamAppOwnershipStatus,
): String {
    val normalizedMessage = rawMessage.trim().ifBlank { "下载失败。" }
    if (!normalizedMessage.contains(STEAM_CDN_UNAUTHORIZED_MESSAGE)) {
        return normalizedMessage
    }

    if (!hasBoundAccount) {
        return "Steam CDN 返回 401，该内容可能需要登录购买过游戏的 Steam 账号才能下载。"
    }

    return when (ownershipStatus) {
        SteamAppOwnershipStatus.NotOwned -> {
            val resolvedGameTitle = gameTitle.ifBlank { "对应游戏" }
            "Steam CDN 返回 401，当前绑定账号未检测到拥有《$resolvedGameTitle》，该内容可能需要登录购买过游戏的 Steam 账号才能下载。"
        }

        SteamAppOwnershipStatus.Owned -> normalizedMessage
        SteamAppOwnershipStatus.Unknown -> "Steam CDN 返回 401，该内容可能需要登录购买过游戏的 Steam 账号才能下载。"
    }
}
