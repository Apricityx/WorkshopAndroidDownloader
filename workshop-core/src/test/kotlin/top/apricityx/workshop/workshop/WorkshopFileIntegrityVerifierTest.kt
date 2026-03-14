package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class WorkshopFileIntegrityVerifierTest {
    @Test
    fun `accepts exact chunk coverage when file hash differs`() {
        val bytes = "assembled-content".encodeToByteArray()
        val file = Files.createTempFile("workshop-integrity", ".bin").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        val manifestFile = ManifestFile(
            path = "mods/example.txt",
            size = bytes.size.toLong(),
            flags = 0u,
            shaContent = sha1("different".encodeToByteArray()),
            linkTarget = null,
            chunks = listOf(
                ManifestChunk(
                    id = ByteArray(20),
                    checksum = steamAdler32(bytes),
                    offset = 0L,
                    compressedLength = bytes.size,
                    uncompressedLength = bytes.size,
                ),
            ),
        )

        val result = WorkshopFileIntegrityVerifier.assess(file, manifestFile)

        assertThat(result).isInstanceOf(AssembledFileValidation.ChunkVerifiedHashMismatch::class.java)
    }

    @Test
    fun `rejects mismatched assembled data when chunk validation fails`() {
        val bytes = "assembled-content".encodeToByteArray()
        val file = Files.createTempFile("workshop-integrity", ".bin").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        val manifestFile = ManifestFile(
            path = "mods/example.txt",
            size = bytes.size.toLong(),
            flags = 0u,
            shaContent = sha1("different".encodeToByteArray()),
            linkTarget = null,
            chunks = listOf(
                ManifestChunk(
                    id = ByteArray(20),
                    checksum = steamAdler32("wrong-content".encodeToByteArray()),
                    offset = 0L,
                    compressedLength = bytes.size,
                    uncompressedLength = bytes.size,
                ),
            ),
        )

        val result = WorkshopFileIntegrityVerifier.assess(file, manifestFile)

        assertThat(result).isInstanceOf(AssembledFileValidation.Invalid::class.java)
        result as AssembledFileValidation.Invalid
        assertThat(result.exactChunkCoverage).isTrue()
        assertThat(result.chunkChecksumsValid).isFalse()
    }

    private fun sha1(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(bytes)
}
