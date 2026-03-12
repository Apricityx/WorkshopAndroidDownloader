package top.apricityx.workshop.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopUpdateVersioningTest {
    @Test
    fun `remote patch version is newer than two segment local version`() {
        assertThat(
            WorkshopUpdateVersioning.isRemoteNewer(
                currentVersion = "1.0",
                remoteVersionTag = "1.0.1",
            ),
        ).isTrue()
    }

    @Test
    fun `hotfix is newer than base patch`() {
        assertThat(
            WorkshopUpdateVersioning.isRemoteNewer(
                currentVersion = "1.0.1",
                remoteVersionTag = "1.0.1-hotfix1",
            ),
        ).isTrue()
    }

    @Test
    fun `normalized equal tags are not treated as newer`() {
        assertThat(
            WorkshopUpdateVersioning.isRemoteNewer(
                currentVersion = "v1.0.1",
                remoteVersionTag = "1.0.1",
            ),
        ).isFalse()
    }

    @Test
    fun `release notes normalization preserves markdown layout`() {
        assertThat(
            WorkshopUpdateVersioning.normalizeReleaseNotesText(
                "\uFEFF# 更新\r\n- 一\r\n\r\n## 修复\r\n- 二\r\n",
            ),
        ).isEqualTo("# 更新\n- 一\n\n## 修复\n- 二")
    }
}
