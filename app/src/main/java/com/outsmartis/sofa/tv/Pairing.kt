package com.outsmartis.sofa.tv

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)

/**
 * Pairing ("polo") on port 6467. [start] makes the TV show a 6-character hex code,
 * [finish] proves we saw it. Blocking: call from an IO thread.
 */
class PairingSession(private val identity: Identity, private val host: String, private val port: Int = PAIRING_PORT) : Closeable {
    private var socket: SSLSocket? = null

    fun start(clientName: String) {
        val s = identity.socketFactory().createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        s.soTimeout = STEP_TIMEOUT_MS
        s.startHandshake()

        send { message(10) { string(1, "atvremote"); string(2, clientName) } }
        expect(11)
        send {
            message(20) {
                message(1) { int(1, ENCODING_HEX); int(2, 6) }
                int(3, ROLE_INPUT)
            }
        }
        expect(20)
        send {
            message(30) {
                message(1) { int(1, ENCODING_HEX); int(2, 6) }
                int(2, ROLE_INPUT)
            }
        }
        expect(31)
    }

    fun finish(code: String) {
        val s = socket ?: throw PairingException("Pairing not started")
        val server = s.session.peerCertificates.first() as X509Certificate
        val secret = secret(identity.publicKey, server.publicKey as RSAPublicKey, code)
            ?: throw PairingException("That code doesn't match. Check the code on the TV.")
        send { message(40) { bytes(1, secret) } }
        expect(41)
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun send(body: ProtoWriter.() -> Unit) {
        val msg = proto {
            int(1, 2)
            int(2, STATUS_OK)
            body()
        }
        writeFrame(socket!!.outputStream, msg)
    }

    private fun expect(field: Int): ProtoMessage {
        val msg = ProtoMessage.parse(readFrame(socket!!.inputStream))
        val status = msg.int(2)
        if (status != STATUS_OK) {
            throw PairingException(
                when (status) {
                    402 -> "Wrong code. Start again and type the code shown on the TV."
                    else -> "The TV refused pairing (status $status)."
                },
            )
        }
        if (!msg.has(field)) throw PairingException("Unexpected reply from the TV: $msg")
        return msg
    }

    companion object {
        const val PAIRING_PORT = 6467
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val STEP_TIMEOUT_MS = 15_000
        private const val STATUS_OK = 200
        private const val ENCODING_HEX = 3
        private const val ROLE_INPUT = 1

        /**
         * SHA-256 over both public keys and the last 4 code digits. The first code byte is a
         * checksum: it must equal the hash's first byte, so a typo is caught before sending.
         */
        fun secret(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
            val c = code.trim().uppercase()
            if (c.length != 6 || c.any { it !in "0123456789ABCDEF" }) return null
            val md = MessageDigest.getInstance("SHA-256")
            md.update(magnitude(client.modulus))
            md.update(magnitude(client.publicExponent))
            md.update(magnitude(server.modulus))
            md.update(magnitude(server.publicExponent))
            md.update(hex(c.substring(2)))
            val hash = md.digest()
            return if (hash[0] == hex(c.substring(0, 2))[0]) hash else null
        }

        private fun magnitude(v: BigInteger): ByteArray {
            val b = v.toByteArray()
            var i = 0
            while (i < b.size - 1 && b[i] == 0.toByte()) i++
            return b.copyOfRange(i, b.size)
        }

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
