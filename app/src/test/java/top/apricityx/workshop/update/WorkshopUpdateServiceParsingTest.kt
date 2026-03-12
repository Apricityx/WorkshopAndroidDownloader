package top.apricityx.workshop.update

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class WorkshopUpdateServiceParsingTest {
    private val service = WorkshopUpdateService(OkHttpClient())

    @Test
    fun `parse latest release extracts version and apk asset`() {
        val parsed = service.parseLatestRelease(
            "{\"tag_name\":\"v1.0.1\"," +
                "\"published_at\":\"2026-03-12T10:00:00Z\"," +
                "\"body\":\"# 更新\\n- 修复下载页面\\n\"," +
                "\"assets\":[{" +
                "\"name\":\"app-release.apk\"," +
                "\"browser_download_url\":\"https://github.com/Apricityx/WorkshopAndroidDownloader/releases/download/v1.0.1/app-release.apk\"" +
                "}]}",
        )

        assertThat(parsed).isNotNull()
        assertThat(parsed?.rawTagName).isEqualTo("v1.0.1")
        assertThat(parsed?.normalizedVersion).isEqualTo("1.0.1")
        assertThat(parsed?.notesText).isEqualTo("# 更新\n- 修复下载页面")
        assertThat(parsed?.assetName).isEqualTo("app-release.apk")
        assertThat(parsed?.assetDownloadUrl).isEqualTo(
            "https://github.com/Apricityx/WorkshopAndroidDownloader/releases/download/v1.0.1/app-release.apk",
        )
    }

    @Test
    fun `mirror url wraps github download url`() {
        assertThat(
            UpdateSource.GH_PROXY_COM.buildUrl("https://github.com/example/release.apk"),
        ).isEqualTo("https://gh-proxy.com/https://github.com/example/release.apk")
        assertThat(
            UpdateSource.OFFICIAL.buildUrl("https://github.com/example/release.apk"),
        ).isEqualTo("https://github.com/example/release.apk")
    }
}
