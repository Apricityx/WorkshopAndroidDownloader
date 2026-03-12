package top.apricityx.workshop.steam.protocol

import top.apricityx.workshop.steam.proto.CMsgMulti
import top.apricityx.workshop.steam.proto.CMsgProtoBufHeader
import com.google.protobuf.MessageLite
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

object SteamPacketCodec {
    const val protoMask: Int = Int.MIN_VALUE
    const val emsgMulti: Int = 1
    const val emsgServiceMethod: Int = 146
    const val emsgServiceMethodResponse: Int = 147
    const val emsgServiceMethodCallFromClient: Int = 151
    const val emsgClientHeartBeat: Int = 703
    const val emsgClientLogOnResponse: Int = 751
    const val emsgClientLoggedOff: Int = 757
    const val emsgClientGetDepotDecryptionKey: Int = 5438
    const val emsgClientGetDepotDecryptionKeyResponse: Int = 5439
    const val emsgServiceMethodCallFromClientNonAuthed: Int = 9804
    const val emsgClientHello: Int = 9805
    const val emsgClientLogon: Int = 5514
    const val clientLogonProtocol: Int = 65581

    fun makeMessageId(emsg: Int, proto: Boolean = true): Int = if (proto) emsg or protoMask else emsg

    fun getBaseMessageId(raw: Int): Int = raw and Int.MAX_VALUE

    fun isProto(raw: Int): Boolean = raw and protoMask != 0

    fun buildWebSocketUri(endpoint: String): String = "wss://$endpoint/cmsocket/"

    fun encode(
        emsg: Int,
        header: CMsgProtoBufHeader,
        body: MessageLite,
    ): ByteArray {
        val headerBytes = header.toByteArray()
        val bodyBytes = body.toByteArray()
        return ByteBuffer
            .allocate(8 + headerBytes.size + bodyBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(makeMessageId(emsg))
            .putInt(headerBytes.size)
            .put(headerBytes)
            .put(bodyBytes)
            .array()
    }

    fun decode(rawPacket: ByteArray): SteamPacket {
        require(rawPacket.size >= 8) { "Steam packet too short: ${rawPacket.size} bytes" }

        val buffer = ByteBuffer.wrap(rawPacket).order(ByteOrder.LITTLE_ENDIAN)
        val rawMessageId = buffer.int
        check(isProto(rawMessageId)) { "Non-protobuf Steam packet is not supported in this demo" }
        val headerLength = buffer.int
        require(headerLength >= 0 && rawPacket.size >= 8 + headerLength) {
            "Invalid Steam packet header length: $headerLength"
        }

        val headerBytes = ByteArray(headerLength)
        buffer.get(headerBytes)
        val bodyBytes = ByteArray(buffer.remaining())
        buffer.get(bodyBytes)

        return SteamPacket(
            emsg = getBaseMessageId(rawMessageId),
            header = CMsgProtoBufHeader.parseFrom(headerBytes),
            body = bodyBytes,
        )
    }

    fun expandMulti(packet: SteamPacket): List<ByteArray> {
        require(packet.emsg == emsgMulti) { "Steam multi decoder only accepts EMsg.Multi" }
        val multi = CMsgMulti.parseFrom(packet.body)
        val stream = if (multi.sizeUnzipped > 0) {
            GZIPInputStream(ByteArrayInputStream(multi.messageBody.toByteArray()))
        } else {
            ByteArrayInputStream(multi.messageBody.toByteArray())
        }

        return buildList {
            while (true) {
                val header = ByteArray(4)
                val read = stream.read(header)
                if (read == -1) {
                    break
                }
                if (read != 4) {
                    throw EOFException("Unexpected EOF while reading multi-packet chunk length")
                }
                val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
                require(length > 0) { "Invalid sub-packet length: $length" }
                val payload = ByteArray(length)
                stream.readNBytes(payload, 0, length).also { bytesRead ->
                    if (bytesRead != length) {
                        throw EOFException("Unexpected EOF while reading multi-packet payload")
                    }
                }
                add(payload)
            }
        }
    }
}
