package com.outsmartis.sofa.tv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Just enough protobuf for the Android TV Remote v2 protocol: varints and
 * length-delimited fields. Messages are small, so a code generator isn't worth it.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        tag(field, 0)
        writeVarint(out, value)
        return this
    }

    fun int(field: Int, value: Int) = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean) = varint(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())
}

fun proto(build: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(build).toByteArray()

/** Parsed message: field number -> values in order (Long for varints, ByteArray for length-delimited). */
class ProtoMessage(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int) = fields.containsKey(field)
    fun long(field: Int): Long = fields[field]?.lastOrNull() as? Long ?: 0L
    fun int(field: Int): Int = long(field).toInt()
    fun bool(field: Int): Boolean = long(field) != 0L
    fun bytes(field: Int): ByteArray = fields[field]?.lastOrNull() as? ByteArray ?: ByteArray(0)
    fun string(field: Int): String = String(bytes(field), Charsets.UTF_8)
    fun message(field: Int): ProtoMessage? = (fields[field]?.lastOrNull() as? ByteArray)?.let { parse(it) }
    fun messages(field: Int): List<ProtoMessage> = fields[field].orEmpty().mapNotNull { (it as? ByteArray)?.let(::parse) }

    override fun toString() = fields.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
        "$k=" + v.joinToString("|") { if (it is ByteArray) printable(it) else it.toString() }
    }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = LinkedHashMap<Int, MutableList<Any>>()
            var pos = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size) throw IllegalArgumentException("truncated varint")
                    val b = data[pos++].toInt() and 0xff
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IllegalArgumentException("varint too long")
                }
            }
            while (pos < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value: Any = when ((key and 7).toInt()) {
                    0 -> varint()
                    1 -> { pos += 8; continue }
                    2 -> {
                        val len = varint().toInt()
                        require(len >= 0 && pos + len <= data.size) { "bad length" }
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    5 -> { pos += 4; continue }
                    else -> throw IllegalArgumentException("wire type ${key and 7}")
                }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }

        private fun printable(b: ByteArray): String {
            val s = String(b, Charsets.UTF_8)
            return if (b.size < 200 && s.all { it.code in 32..126 }) "\"$s\"" else
                runCatching { parse(b).toString() }.getOrElse { "<${b.size} bytes>" }
        }
    }
}

fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (true) {
        if (v and 0x7fL.inv() == 0L) {
            out.write(v.toInt())
            return
        }
        out.write(((v and 0x7f) or 0x80).toInt())
        v = v ushr 7
    }
}

/** Both TV sockets frame each message with a varint length prefix. */
fun writeFrame(out: OutputStream, message: ByteArray) {
    val buf = ByteArrayOutputStream(message.size + 5)
    writeVarint(buf, message.size.toLong())
    buf.write(message)
    out.write(buf.toByteArray())
    out.flush()
}

fun readFrame(input: InputStream): ByteArray {
    var len = 0
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException("connection closed")
        len = len or ((b and 0x7f) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        if (shift > 28) throw IllegalArgumentException("frame length too long")
    }
    val data = ByteArray(len)
    var read = 0
    while (read < len) {
        val n = input.read(data, read, len - read)
        if (n < 0) throw EOFException("connection closed")
        read += n
    }
    return data
}
