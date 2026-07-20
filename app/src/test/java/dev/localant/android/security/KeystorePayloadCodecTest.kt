package dev.localant.android.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystorePayloadCodecTest {
    @Test
    fun roundTrip_preservesIvAndCiphertext() {
        val iv = byteArrayOf(1, 2, 3, 4)
        val ciphertext = byteArrayOf(9, 8, 7, 6, 5)

        val decoded = KeystorePayloadCodec.decode(KeystorePayloadCodec.encode(iv, ciphertext))!!

        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun encodedPayload_isVersionedAndUrlSafe() {
        val encoded = KeystorePayloadCodec.encode(byteArrayOf(0, -1, 4), byteArrayOf(12, 13, 14))
        assertTrue(encoded.startsWith("v1."))
        assertTrue(encoded.none { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun malformedPayload_returnsNull() {
        assertNull(KeystorePayloadCodec.decode("v1.not-base64!.still-bad"))
        assertNull(KeystorePayloadCodec.decode("v1.only-two"))
    }

    @Test
    fun unsupportedVersion_returnsNull() {
        assertNull(KeystorePayloadCodec.decode("v2.AQ.Ag"))
    }

    @Test
    fun blankOrEmptyData_returnsNull() {
        assertNull(KeystorePayloadCodec.decode(null))
        assertNull(KeystorePayloadCodec.decode(""))
        assertNull(KeystorePayloadCodec.decode("v1..Ag"))
        assertNull(KeystorePayloadCodec.decode("v1.AQ."))
    }
}
