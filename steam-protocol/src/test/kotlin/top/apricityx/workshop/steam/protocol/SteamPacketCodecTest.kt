package top.apricityx.workshop.steam.protocol

import top.apricityx.workshop.steam.proto.CMsgClientHello
import top.apricityx.workshop.steam.proto.CMsgMulti
import top.apricityx.workshop.steam.proto.CMsgProtoBufHeader
import com.google.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SteamPacketCodecTest {
    @Test
    fun `encode then decode preserves header and body`() {
        val packetBytes = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgClientHello,
            header = CMsgProtoBufHeader.newBuilder()
                .setClientSessionid(42)
                .setSteamid(99)
                .build(),
            body = CMsgClientHello.newBuilder().setProtocolVersion(65581).build(),
        )

        val packet = SteamPacketCodec.decode(packetBytes)

        assertThat(packet.emsg).isEqualTo(SteamPacketCodec.emsgClientHello)
        assertThat(packet.header.clientSessionid).isEqualTo(42)
        assertThat(CMsgClientHello.parseFrom(packet.body).protocolVersion).isEqualTo(65581)
    }

    @Test
    fun `expandMulti returns nested packets`() {
        val nested = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgClientHello,
            header = CMsgProtoBufHeader.getDefaultInstance(),
            body = CMsgClientHello.newBuilder().setProtocolVersion(1).build(),
        )

        val messageBody = ByteBuffer.allocate(4 + nested.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(nested.size)
            .put(nested)
            .array()

        val multi = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgMulti,
            header = CMsgProtoBufHeader.getDefaultInstance(),
            body = CMsgMulti.newBuilder().setMessageBody(ByteString.copyFrom(messageBody)).build(),
        )

        val expanded = SteamPacketCodec.expandMulti(SteamPacketCodec.decode(multi))

        assertThat(expanded).hasSize(1)
        assertThat(CMsgClientHello.parseFrom(SteamPacketCodec.decode(expanded.single()).body).protocolVersion).isEqualTo(1)
    }
}
