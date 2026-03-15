package top.apricityx.workshop.workshop

import top.apricityx.workshop.steam.proto.ContentManifestMetadata
import top.apricityx.workshop.steam.proto.ContentManifestPayload
import top.apricityx.workshop.steam.proto.ContentManifestSignature
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DepotManifestParserTest {
    @Test
    fun `parse protobuf manifest sections`() {
        val payload = ContentManifestPayload.newBuilder()
            .addMappings(
                ContentManifestPayload.FileMapping.newBuilder()
                    .setFilename("mods/example.txt")
                    .setSize(7)
                    .setFlags(0)
                    .setShaContent(com.google.protobuf.ByteString.copyFrom(byteArrayOf(1, 2, 3)))
                    .addChunks(
                        ContentManifestPayload.FileMapping.ChunkData.newBuilder()
                            .setSha(com.google.protobuf.ByteString.copyFrom(byteArrayOf(9, 9, 9)))
                            .setCrc(123)
                            .setOffset(0)
                            .setCbOriginal(7)
                            .setCbCompressed(7)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val metadata = ContentManifestMetadata.newBuilder()
            .setDepotId(480)
            .setGidManifest(9999)
            .setCreationTime(1700000000)
            .setCrcEncrypted(456)
            .build()

        val bytes = ByteArrayOutputStream().apply {
            writeSection(0x71F617D0u, payload.toByteArray())
            writeSection(0x1F4812BEu, metadata.toByteArray())
            writeSection(0x1B81B817u, ContentManifestSignature.getDefaultInstance().toByteArray())
            writeUInt32(0x32C415ABu)
        }.toByteArray()

        val manifest = DepotManifestParser.parse(bytes)

        assertThat(manifest.depotId).isEqualTo(480u)
        assertThat(manifest.manifestId).isEqualTo(9999uL)
        assertThat(manifest.files).hasSize(1)
        assertThat(manifest.files.single().path).isEqualTo("mods/example.txt")
        assertThat(manifest.uniqueChunks()).hasSize(1)
    }

    @Test
    fun `decrypt encrypted manifest filenames`() {
        val depotKey = ByteArray(32) { index -> (index + 1).toByte() }
        val encryptedB = encryptManifestName("mods/b.txt", depotKey, ByteArray(16) { it.toByte() }) + "\u0000 "
        val encryptedA = encryptManifestName("mods/a.txt", depotKey, ByteArray(16) { (it + 16).toByte() }) + "\n"
        val encryptedLink = encryptManifestName("mods/link.txt", depotKey, ByteArray(16) { (it + 32).toByte() }) + "\t"

        val manifest = DepotManifest(
            depotId = 480u,
            manifestId = 9999uL,
            createdAt = Instant.EPOCH,
            encryptedCrc = 0u,
            filenamesEncrypted = true,
            files = listOf(
                ManifestFile(
                    path = encryptedB,
                    size = 0,
                    flags = 0u,
                    shaContent = ByteArray(0),
                    linkTarget = null,
                    chunks = emptyList(),
                ),
                ManifestFile(
                    path = encryptedA,
                    size = 0,
                    flags = 0u,
                    shaContent = ByteArray(0),
                    linkTarget = encryptedLink,
                    chunks = emptyList(),
                ),
            ),
        )

        val decrypted = manifest.decryptFilenames(depotKey)

        assertThat(decrypted.filenamesEncrypted).isFalse()
        assertThat(decrypted.files.map(ManifestFile::path)).containsExactly("mods/a.txt", "mods/b.txt").inOrder()
        assertThat(decrypted.files.first().linkTarget).isEqualTo("mods/link.txt")
    }

    @Test
    fun `decrypt encrypted manifest filenames with embedded line breaks`() {
        val depotKey = ByteArray(32) { index -> (index + 11).toByte() }
        val encrypted = encryptManifestName(
            name = "mods/example/file.txt",
            depotKey = depotKey,
            iv = ByteArray(16) { (it + 48).toByte() },
        )
        val wrapped = encrypted.chunked(31).joinToString("\r\n")

        val manifest = DepotManifest(
            depotId = 480u,
            manifestId = 9999uL,
            createdAt = Instant.EPOCH,
            encryptedCrc = 0u,
            filenamesEncrypted = true,
            files = listOf(
                ManifestFile(
                    path = wrapped,
                    size = 0,
                    flags = 0u,
                    shaContent = ByteArray(0),
                    linkTarget = null,
                    chunks = emptyList(),
                ),
            ),
        )

        val decrypted = manifest.decryptFilenames(depotKey)

        assertThat(decrypted.files.single().path).isEqualTo("mods/example/file.txt")
    }

    @Test
    fun `parse trims manifest strings before storing`() {
        val payload = ContentManifestPayload.newBuilder()
            .addMappings(
                ContentManifestPayload.FileMapping.newBuilder()
                    .setFilename("mods\\example.txt\u0000 ")
                    .setLinktarget("mods\\target.txt\u0000 ")
                    .setSize(0)
                    .build(),
            )
            .build()

        val metadata = ContentManifestMetadata.newBuilder()
            .setDepotId(480)
            .setGidManifest(9999)
            .setCreationTime(1700000000)
            .setFilenamesEncrypted(false)
            .build()

        val bytes = ByteArrayOutputStream().apply {
            writeSection(0x71F617D0u, payload.toByteArray())
            writeSection(0x1F4812BEu, metadata.toByteArray())
            writeUInt32(0x32C415ABu)
        }.toByteArray()

        val manifest = DepotManifestParser.parse(bytes)

        assertThat(manifest.files.single().path).isEqualTo("mods/example.txt")
        assertThat(manifest.files.single().linkTarget).isEqualTo("mods/target.txt")
    }

    private fun ByteArrayOutputStream.writeSection(magic: UInt, payload: ByteArray) {
        writeUInt32(magic)
        writeUInt32(payload.size.toUInt())
        write(payload)
    }

    private fun ByteArrayOutputStream.writeUInt32(value: UInt) {
        write(
            ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value.toInt())
                .array(),
        )
    }

    private fun encryptManifestName(
        name: String,
        depotKey: ByteArray,
        iv: ByteArray,
    ): String {
        val key = SecretKeySpec(depotKey, "AES")
        val encryptedIv = Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key)
            doFinal(iv)
        }
        val encryptedName = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            doFinal(name.encodeToByteArray() + byteArrayOf(0))
        }
        return Base64.getEncoder().encodeToString(encryptedIv + encryptedName)
    }
}
