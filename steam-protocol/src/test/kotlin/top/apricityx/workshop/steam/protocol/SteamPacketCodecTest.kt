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

    @Test
    fun `decodeLegacyServerUnavailableBody parses extended legacy packet`() {
        val body = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(99L)
            .putInt(SteamPacketCodec.emsgServiceMethodCallFromClient)
            .putInt(12)
            .array()

        val rawPacket = ByteBuffer.allocate(36 + body.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(SteamPacketCodec.emsgClientServerUnavailable)
            .put(36.toByte())
            .putShort(2)
            .putLong(123L)
            .putLong(456L)
            .put(239.toByte())
            .putLong(789L)
            .putInt(42)
            .put(body)
            .array()

        val packet = SteamPacketCodec.decodeLegacyPacket(rawPacket)
        val decoded = SteamPacketCodec.decodeLegacyServerUnavailableBody(packet)

        assertThat(packet.header.targetJobId).isEqualTo(123L)
        assertThat(packet.header.sourceJobId).isEqualTo(456L)
        assertThat(packet.header.sessionId).isEqualTo(42)
        assertThat(decoded.jobIdSent).isEqualTo(99L)
        assertThat(decoded.emsgSent).isEqualTo(SteamPacketCodec.emsgServiceMethodCallFromClient)
        assertThat(decoded.serverTypeUnavailable).isEqualTo(12)
    }

    @Test
    fun `decodeLegacyLoggedOffBody parses reconnect hints`() {
        val body = ByteBuffer.allocate(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(42)
            .putInt(5)
            .putInt(30)
            .array()

        val rawPacket = ByteBuffer.allocate(36 + body.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(SteamPacketCodec.emsgClientLoggedOff)
            .put(36.toByte())
            .putShort(2)
            .putLong(-1L)
            .putLong(-1L)
            .put(239.toByte())
            .putLong(76561197960287930L)
            .putInt(77)
            .put(body)
            .array()

        val packet = SteamPacketCodec.decodeLegacyPacket(rawPacket)
        val decoded = SteamPacketCodec.decodeLegacyLoggedOffBody(packet)

        assertThat(packet.header.steamId).isEqualTo(76561197960287930L)
        assertThat(decoded.resultCode).isEqualTo(42)
        assertThat(decoded.minReconnectHintSeconds).isEqualTo(5)
        assertThat(decoded.maxReconnectHintSeconds).isEqualTo(30)
    }
}
