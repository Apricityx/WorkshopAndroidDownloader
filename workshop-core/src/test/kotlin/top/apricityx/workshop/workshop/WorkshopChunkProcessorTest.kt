package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class WorkshopChunkProcessorTest {
    @Test
    fun process_decryptsAndDecompressesVzipDepotChunk() {
        val rawChunk = Base64.getDecoder().decode(
            "vdDIlLcZWSWRFk9ZLqZp1pDPrdSQEsWOoMWNgqL1CudjrPLE3yq7rCyHDU4+mRBuz1RzT9iiqrbLnQkM+aAG2tjeqlhsiLhv02AskjTfsYVwikUvl1vLhqP5gyOP8JIJVD3JPB8V+n+TFnnTg7vZUqki44AU+RMj8iDV7NT+gwY8jka9N5gX8S4cshd3kb2TE2IPSx2NKaeaKn2v3UNJ057TmTGU1oZ0R8Cx8lvVoY3ZEV5ObaApTycrkdulq1q2aBzkc3X0KNa7VRBoN1XjMXOSZ9hhKuUxRvYdncaLLxn1CT43JOQZkV6+Ocp0P6FZifm3tS9fWRmZVXWcO/lzUGJ3OCAatYB38t6qDymUIM9cUXz9ygfGQfG+tQnciy0P+R2B/iIJQ865G6IxWldQJA==",
        )

        val chunk = ManifestChunk(
            id = ByteArray(0),
            checksum = 2894626744u,
            offset = 0L,
            compressedLength = 304,
            uncompressedLength = 798,
        )

        val depotKey = byteArrayOf(
            0xE5.toByte(), 0xF6.toByte(), 0xAE.toByte(), 0xD5.toByte(), 0x5E.toByte(), 0x9E.toByte(), 0xCE.toByte(), 0x42.toByte(),
            0x9E.toByte(), 0x56.toByte(), 0xB8.toByte(), 0x13.toByte(), 0xFB.toByte(), 0xF6.toByte(), 0xBF.toByte(), 0xE9.toByte(),
            0x24.toByte(), 0xF3.toByte(), 0xCF.toByte(), 0x72.toByte(), 0x97.toByte(), 0x2F.toByte(), 0xDB.toByte(), 0xD0.toByte(),
            0x57.toByte(), 0x1F.toByte(), 0xFC.toByte(), 0xAD.toByte(), 0x9F.toByte(), 0x2F.toByte(), 0x7D.toByte(), 0xAA.toByte(),
        )

        val processed = ChunkProcessor.process(rawChunk, chunk, depotKey)
        val hash = MessageDigest.getInstance("SHA-1").digest(processed).joinToString("") { "%02X".format(it) }

        assertThat(processed.size).isEqualTo(798)
        assertThat(hash).isEqualTo("7B8567D9B3C09295CDBF4978C32B348D8E76C750")
    }
}
