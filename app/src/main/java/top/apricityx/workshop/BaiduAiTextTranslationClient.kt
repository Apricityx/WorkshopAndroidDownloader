package top.apricityx.workshop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class BaiduTranslationCredentials(
    val appId: String = "",
    val apiKey: String = "",
) {
    fun isConfigured(): Boolean = appId.isNotBlank() && apiKey.isNotBlank()
}

class BaiduAiTextTranslationClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = "https://fanyi-api.baidu.com/".toHttpUrl(),
) {
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        credentials: BaiduTranslationCredentials,
    ): String = withContext(Dispatchers.IO) {
        require(credentials.isConfigured()) {
            "请先配置百度大模型文本翻译的 AppID 和 API Key。"
        }

        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return@withContext normalizedText
        }

        val requestBody = buildJsonObject {
            put("appid", credentials.appId)
            put("from", sourceLanguage)
            put("to", targetLanguage)
            put("q", normalizedText)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("ait/api/aiTextTranslate").build())
            .header("Authorization", "Bearer ${credentials.apiKey}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            val root = parsePayload(payload)
            val errorCode = root["error_code"]?.jsonPrimitive?.contentOrNull
            if (!response.isSuccessful || !errorCode.isNullOrBlank() && errorCode != "0") {
                throw IllegalStateException(buildErrorMessage(errorCode, root["error_msg"]?.jsonPrimitive?.contentOrNull))
            }

            extractTranslatedText(root)?.let { translatedText ->
                return@withContext translatedText
            }

            throw IllegalStateException("百度大模型文本翻译返回了无法识别的结果。")
        }
    }

    private fun parsePayload(payload: String): JsonObject {
        if (payload.isBlank()) {
            throw IllegalStateException("百度大模型文本翻译返回了空响应。")
        }
        return runCatching {
            json.parseToJsonElement(payload).jsonObject
        }.getOrElse {
            throw IllegalStateException("百度大模型文本翻译返回了无法解析的响应。")
        }
    }

    private fun buildErrorMessage(
        errorCode: String?,
        errorMessage: String?,
    ): String {
        val normalizedMessage = errorMessage?.takeIf(String::isNotBlank) ?: "请求失败"
        return if (errorCode.isNullOrBlank()) {
            "百度大模型文本翻译失败：$normalizedMessage。"
        } else {
            "百度大模型文本翻译失败：$normalizedMessage（错误码：$errorCode）。"
        }
    }

    private fun extractTranslatedText(root: JsonObject): String? {
        val data = root["data"]
        val directDataText = data
            ?.takeIf { it !is JsonObject && it !is JsonArray }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
        if (directDataText != null) {
            return directDataText
        }

        val preferredMatches = collectPreferredTranslatedTexts(data).ifEmpty {
            collectPreferredTranslatedTexts(root)
        }
        return preferredMatches
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString("\n")
    }

    private fun collectPreferredTranslatedTexts(element: JsonElement?): List<String> {
        return when (element) {
            is JsonObject -> {
                val directMatches = PREFERRED_RESULT_KEYS.mapNotNull { key ->
                    val value = element[key]
                    if (value == null || value is JsonObject || value is JsonArray) {
                        null
                    } else {
                        value.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
                    }
                }
                if (directMatches.isNotEmpty()) {
                    return directMatches
                }

                element.values.flatMap(::collectPreferredTranslatedTexts)
            }

            is JsonArray -> element.flatMap(::collectPreferredTranslatedTexts)
            else -> emptyList()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PREFERRED_RESULT_KEYS = listOf(
            "dst",
            "translation",
            "translatedText",
            "targetText",
            "target_text",
            "result",
            "output",
        )
    }
}

fun mapMlKitLanguageToBaiduLanguage(language: String): String? =
    when (language) {
        "zh" -> "zh"
        "en" -> "en"
        "ja" -> "jp"
        "ko" -> "kor"
        "de" -> "de"
        "ru" -> "ru"
        "es" -> "spa"
        "fr" -> "fra"
        "it" -> "it"
        "pt" -> "pt"
        "ar" -> "ara"
        "fi" -> "fin"
        "sk" -> "sk"
        "sv" -> "swe"
        else -> null
    }
