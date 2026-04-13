package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BaiduTranslationReferenceTest {
    @Test
    fun buildBaiduModDescriptionReference_includesModAndGameContext() {
        val reference = buildBaiduModDescriptionReference(
            modTitle = "Skip The Spire",
            gameTitle = "Slay the Spire",
        )

        assertThat(reference).contains("模组名：Skip The Spire。")
        assertThat(reference).contains("所属游戏：Slay the Spire。")
        assertThat(reference).contains("Steam 创意工坊模组说明")
        assertThat(reference).contains("不要补充原文没有的信息")
    }

    @Test
    fun buildBaiduModDescriptionReference_truncatesLongValuesToDocumentLimit() {
        val longValue = "A".repeat(200)

        val reference = buildBaiduModDescriptionReference(
            modTitle = longValue,
            gameTitle = longValue,
        )

        assertThat(reference.length).isAtMost(500)
        assertThat(reference).contains("模组名：${"A".repeat(80)}。")
        assertThat(reference).contains("所属游戏：${"A".repeat(80)}。")
    }
}
