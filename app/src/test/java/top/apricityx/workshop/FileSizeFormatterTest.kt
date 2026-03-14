package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileSizeFormatterTest {
    @Test
    fun formatBinaryFileSize_usesAsciiUnits() {
        assertThat(formatBinaryFileSize(0L)).isEqualTo("0 B")
        assertThat(formatBinaryFileSize(999L)).isEqualTo("999 B")
        assertThat(formatBinaryFileSize(1024L)).isEqualTo("1.0 KB")
        assertThat(formatBinaryFileSize(1536L)).isEqualTo("1.5 KB")
        assertThat(formatBinaryFileSize(1024L * 1024L)).isEqualTo("1.0 MB")
        assertThat(formatBinaryFileSize(1024L * 1024L * 1024L)).isEqualTo("1.0 GB")
    }
}
