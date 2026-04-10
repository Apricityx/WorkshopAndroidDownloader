package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.zip.Adler32

class SteamChecksumTest {
    @Test
    fun `steam adler32 uses zero seed`() {
        val input = "hello world".encodeToByteArray()

        val steamChecksum = steamAdler32(input)
        val javaChecksum = Adler32().apply { update(input) }.value.toUInt()

        assertThat(steamChecksum).isEqualTo(436208732u)
        assertThat(javaChecksum).isNotEqualTo(steamChecksum)
    }
}
