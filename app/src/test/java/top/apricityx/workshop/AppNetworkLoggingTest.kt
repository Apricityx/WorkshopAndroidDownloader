package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class AppNetworkLoggingTest {
    @Test
    fun redactedForNetworkLog_masks_sensitive_query_values() {
        val url =
            "https://example.com/path?token=abc123&search=basemod&access_token=secret&sessionid=foo".toHttpUrl()

        val redacted = url.redactedForNetworkLog()

        assertThat(redacted).contains("token=***")
        assertThat(redacted).contains("access_token=***")
        assertThat(redacted).contains("sessionid=***")
        assertThat(redacted).contains("search=basemod")
        assertThat(redacted).doesNotContain("abc123")
        assertThat(redacted).doesNotContain("secret")
        assertThat(redacted).doesNotContain("foo")
    }
}
