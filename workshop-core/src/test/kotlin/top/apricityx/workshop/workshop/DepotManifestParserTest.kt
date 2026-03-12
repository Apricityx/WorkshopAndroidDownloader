package top.apricityx.workshop.workshop

import top.apricityx.workshop.steam.proto.ContentManifestMetadata
import top.apricityx.workshop.steam.proto.ContentManifestPayload
import top.apricityx.workshop.steam.proto.ContentManifestSignature
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
}
