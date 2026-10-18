package net.rizkiepratama.hako.engine.tether

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PtpPacket(
    val type: Int,
    val code: Int,
    val transactionId: Int,
    val params: List<Int> = emptyList(),
    val payload: ByteArray = ByteArray(0)
) {
    val length: Int
        get() = PtpConstants.HEADER_SIZE + (params.size * 4) + payload.size

    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(length)
        buffer.putShort(type.toShort())
        buffer.putShort(code.toShort())
        buffer.putInt(transactionId)
        for (param in params) {
            buffer.putInt(param)
        }
        if (payload.isNotEmpty()) {
            buffer.put(payload)
        }
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PtpPacket
        if (type != other.type) return false
        if (code != other.code) return false
        if (transactionId != other.transactionId) return false
        if (params != other.params) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + code
        result = 31 * result + transactionId
        result = 31 * result + params.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        fun decode(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset): PtpPacket? {
            if (count < PtpConstants.HEADER_SIZE) return null
            val buffer = ByteBuffer.wrap(bytes, offset, count).order(ByteOrder.LITTLE_ENDIAN)
            val pktLength = buffer.int
            if (pktLength < PtpConstants.HEADER_SIZE) return null
            val type = buffer.short.toInt() and 0xFFFF
            val code = buffer.short.toInt() and 0xFFFF
            val transactionId = buffer.int

            val availableBytes = count - PtpConstants.HEADER_SIZE
            val payloadLen = (pktLength - PtpConstants.HEADER_SIZE).coerceAtMost(availableBytes).coerceAtLeast(0)
            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            val paramsList = mutableListOf<Int>()
            if (payload.size >= 4) {
                val paramBuf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                while (paramBuf.remaining() >= 4 && paramsList.size < 5) {
                    paramsList.add(paramBuf.int)
                }
            }

            return PtpPacket(type, code, transactionId, paramsList, payload)
        }

        fun createCommand(code: Int, transactionId: Int, vararg params: Int): PtpPacket {
            return PtpPacket(
                type = PtpConstants.TYPE_COMMAND,
                code = code,
                transactionId = transactionId,
                params = params.toList()
            )
        }
    }
}
