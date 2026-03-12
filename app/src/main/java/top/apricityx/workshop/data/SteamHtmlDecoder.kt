package top.apricityx.workshop.data

internal object SteamHtmlDecoder {
    private val numericEntityRegex = Regex("""&#(x?[0-9A-Fa-f]+);""")
    private val htmlTagRegex = Regex("""<[^>]+>""")

    fun stripTagsAndDecode(value: String): String = decode(value.replace(htmlTagRegex, " "))

    fun decode(value: String): String {
        val withNumericEntities = numericEntityRegex.replace(value) { match ->
            val token = match.groupValues[1]
            val codePoint = if (token.startsWith("x", ignoreCase = true)) {
                token.substring(1).toIntOrNull(16)
            } else {
                token.toIntOrNull()
            }
            codePoint?.let { String(Character.toChars(it)) } ?: match.value
        }

        return withNumericEntities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
