package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class BaiduAiTextTranslationClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun translate_sendsReferenceWithLlmModelType() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"error_code":"0","data":{"translation":"你好"}}""")
                .build(),
        )
        val client = BaiduAiTextTranslationClient(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
        )

        val translatedText = client.translate(
            text = "hello",
            sourceLanguage = "en",
            targetLanguage = "zh",
            credentials = BaiduTranslationCredentials(
                appId = "app-id",
                apiKey = "api-key",
            ),
            reference = "结合 Slay the Spire 模组语境翻译",
        )

        assertThat(translatedText).isEqualTo("你好")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/ait/api/aiTextTranslate")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer api-key")

        val requestBody = Json.parseToJsonElement(request.body?.utf8().orEmpty()).toString()
        assertThat(requestBody).contains(""""appid":"app-id"""")
        assertThat(requestBody).contains(""""from":"en"""")
        assertThat(requestBody).contains(""""to":"zh"""")
        assertThat(requestBody).contains(""""q":"hello"""")
        assertThat(requestBody).contains(""""model_type":"llm"""")
        assertThat(requestBody).contains(""""reference":"结合 Slay the Spire 模组语境翻译"""")
    }
}
