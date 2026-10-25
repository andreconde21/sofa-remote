package com.outsmartis.sofa.tv

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * The phone's client certificate. The TV remembers it after pairing, so one identity
 * is shared by every TV and must survive app updates (it lives in the app's files dir).
 */
class Identity(val certificate: X509Certificate, private val key: PrivateKey) {

    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey

    /** TLS with our client cert. TVs use self-signed certs, so the server isn't verified here. */
    fun socketFactory(): SSLSocketFactory {
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, key, PASS, arrayOf(certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, PASS) }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(TrustAll), SecureRandom())
        return ctx.socketFactory
    }

    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val ALIAS = "sofa"
        private val PASS = "sofa".toCharArray()

        fun loadOrCreate(file: File, commonName: String = "Sofa Remote"): Identity {
            if (file.exists()) {
                runCatching {
                    val ks = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, PASS) } }
                    return Identity(ks.getCertificate(ALIAS) as X509Certificate, ks.getKey(ALIAS, PASS) as PrivateKey)
                }
            }
            val id = generate(commonName)
            val ks = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(ALIAS, id.key, PASS, arrayOf(id.certificate))
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { ks.store(it, PASS) }
            tmp.renameTo(file)
            return id
        }

        fun generate(commonName: String): Identity {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val sha256Rsa = Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.NULL)
            val name = Der.seq(Der.set(Der.seq(Der.oid("2.5.4.3"), Der.utf8(commonName))))
            val tbs = Der.seq(
                Der.explicit(0, Der.integer(BigInteger.valueOf(2))),
                Der.integer(BigInteger(63, SecureRandom())),
                sha256Rsa,
                name,
                Der.seq(Der.utcTime(Date(System.currentTimeMillis() - 86_400_000L)), Der.utcTime(Date(2524607999000L))),
                name,
                pair.public.encoded,
            )
            val sig = Signature.getInstance("SHA256withRSA").run {
                initSign(pair.private)
                update(tbs)
                sign()
            }
            val der = Der.seq(tbs, sha256Rsa, Der.bitString(sig))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
            return Identity(cert, pair.private)
        }
    }
}

/** Minimal DER encoder for building one self-signed certificate. */
internal object Der {
    val NULL = byteArrayOf(0x05, 0x00)

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        when {
            len < 0x80 -> out.write(len)
            len < 0x100 -> { out.write(0x81); out.write(len) }
            len < 0x10000 -> { out.write(0x82); out.write(len shr 8); out.write(len and 0xff) }
            else -> { out.write(0x83); out.write(len shr 16); out.write((len shr 8) and 0xff); out.write(len and 0xff) }
        }
        out.write(content)
        return out.toByteArray()
    }

    fun seq(vararg parts: ByteArray) = tlv(0x30, concat(parts))
    fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))
    fun explicit(n: Int, inner: ByteArray) = tlv(0xa0 or n, inner)
    fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())
    fun utf8(s: String) = tlv(0x0c, s.toByteArray(Charsets.UTF_8))
    fun bitString(b: ByteArray) = tlv(0x03, byteArrayOf(0) + b)

    fun utcTime(d: Date): ByteArray {
        val f = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(0x17, f.format(d).toByteArray(Charsets.US_ASCII))
    }

    fun oid(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (p in parts.drop(2)) {
            val stack = ArrayList<Int>()
            var v = p
            stack.add((v and 0x7f).toInt())
            v = v shr 7
            while (v > 0) {
                stack.add(((v and 0x7f) or 0x80).toInt())
                v = v shr 7
            }
            stack.reversed().forEach(out::write)
        }
        return tlv(0x06, out.toByteArray())
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach(out::write)
        return out.toByteArray()
    }
}
