package com.outsmartis.sofa.tv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

class ProtocolTest {
    @Test fun keyInjectMatchesProtobufEncoding() {
        // remote_key_inject { key_code: KEYCODE_DPAD_CENTER, direction: SHORT } from the reference protobuf
        val bytes = proto { message(10) { int(1, Keys.CENTER); int(2, Keys.SHORT) } }
        assertArrayEquals(byteArrayOf(0x52, 0x04, 0x08, 0x17, 0x10, 0x03), bytes)
    }

    @Test fun parsesNestedMessagesAndNegativeInts() {
        val bytes = proto {
            int(1, -5)
            message(21) { int(1, 7); message(3) { message(2) { string(3, "olá") } } }
        }
        val msg = ProtoMessage.parse(bytes)
        assertEquals(-5, msg.int(1))
        val edit = msg.message(21)!!
        assertEquals(7, edit.int(1))
        assertEquals("olá", edit.messages(3).single().message(2)!!.string(3))
    }

    @Test fun framesRoundTrip() {
        val out = ByteArrayOutputStream()
        val big = ByteArray(300) { it.toByte() }
        writeFrame(out, big)
        writeFrame(out, byteArrayOf(1, 2))
        val input = ByteArrayInputStream(out.toByteArray())
        assertArrayEquals(big, readFrame(input))
        assertArrayEquals(byteArrayOf(1, 2), readFrame(input))
    }

    private fun key(hexModulus: String): RSAPublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(hexModulus, 16), BigInteger.valueOf(65537))) as RSAPublicKey

    @Test fun pairingSecretMatchesReferenceImplementation() {
        // Vector computed with the androidtvremote2 algorithm (Python) for these keys and code.
        val client = key("C3" + "5A".repeat(127))
        val server = key("D1" + "7B".repeat(127))
        val secret = PairingSession.secret(client, server, "604f2a")!!
        assertEquals("60b1978225da6d2b141ceb8b6d36e13f9e549643103d180a8b50c28bac5d222f", secret.joinToString("") { "%02x".format(it) })
        // A typo fails the checksum byte instead of being sent to the TV.
        assertNull(PairingSession.secret(client, server, "614F2A"))
        assertNull(PairingSession.secret(client, server, "60F2A"))
    }

    @Test fun generatedIdentityIsAValidSelfSignedCertificate() {
        val id = Identity.generate("Sofa test")
        id.certificate.checkValidity()
        id.certificate.verify(id.certificate.publicKey)
        assertTrue(id.certificate.subjectX500Principal.name.contains("Sofa test"))
        assertEquals(2048, id.publicKey.modulus.bitLength())
        id.socketFactory() // key + cert load into a TLS context
    }

    @Test fun identitySurvivesReload() {
        val f = java.io.File.createTempFile("sofa", ".p12").apply { delete() }
        val a = Identity.loadOrCreate(f)
        val b = Identity.loadOrCreate(f)
        assertEquals(a.certificate, b.certificate)
        f.delete()
    }

    @Test fun typingPlansBackspacesThenInsert() {
        assertEquals(TypingPlan(0, "lo", emptyList(), "hel"), Typist.plan("hel", "hello", true))
        // autocorrect "teh" -> "the ": two backspaces, then the new tail
        assertEquals(TypingPlan(2, "he ", emptyList(), "t"), Typist.plan("teh", "the ", true))
        // no IME channel: key presses (lowercase letters, space)
        assertEquals(TypingPlan(1, "", listOf(46, 62, 47), "ca"), Typist.plan("cat", "car s", false))
    }
}
