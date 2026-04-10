package top.apricityx.workshop.data

internal object SteamHtmlDecoder {
    private val numericEntityRegex = Regex("""&#(x?[0-9A-Fa-f]+);""")
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val emoticonImageRegex = Regex("""(?is)<img\b[^>]*\balt="([^"]+)"[^>]*\bclass="[^"]*\bemoticon\b[^"]*"[^>]*>""")
    private val bbCodeGenericTagRegex = Regex("""\[(?:/?[A-Za-z][A-Za-z0-9_]*|\*)(?:=[^\]]+)?\]""")
    private val bbCodeMediaTagRegex = Regex(
        """(?is)\[(?:img|previewyoutube|previewyoutubehd)[^\]]*].*?\[/(?:img|previewyoutube|previewyoutubehd)]""",
    )
    private val whitespaceRegex = Regex("""\s+""")
    private val inlineWhitespaceRegex = Regex("""[^\S\n]+""")

    fun stripTagsAndDecode(value: String): String = decode(value.replace(htmlTagRegex, " "))

    fun decode(value: String): String {
        return decodeEntities(value)
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun decodePreservingLineBreaks(value: String): String =
        decodeEntities(value)
            .replace(Regex("""\r\n?"""), "\n")
            .lines()
            .joinToString("\n") { line ->
                line.replace(inlineWhitespaceRegex, " ").trim()
            }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

    fun decodeWorkshopApiDescription(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(bbCodeMediaTagRegex, " ")
                .replace(Regex("""(?i)\[\*\]"""), "\n• ")
                .replace(Regex("""(?i)\[/?(?:h[1-6]|list|olist|quote|p|center|left|right)\]"""), "\n")
                .replace(htmlTagRegex, " ")
                .replace(bbCodeGenericTagRegex, " "),
        )
    }

    fun decodeWorkshopHtmlDescription(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)<li[^>]*>"""), "• ")
                .replace(Regex("""(?i)</li\s*>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(htmlTagRegex, " "),
        )
    }

    fun decodeWorkshopChangeNotes(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)<li[^>]*>"""), "- ")
                .replace(Regex("""(?i)</li\s*>"""), "\n")
                .replace(Regex("""(?i)<(?:ul|ol)[^>]*>"""), "\n")
                .replace(Regex("""(?i)</(?:ul|ol)\s*>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(htmlTagRegex, " "),
        )
    }

    fun decodeWorkshopComment(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(emoticonImageRegex) { match -> " ${match.groupValues[1]} " }
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(htmlTagRegex, " "),
        )
    }

    private fun decodeEntities(value: String): String {
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
    }
}
