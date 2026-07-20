package dev.localant.android.security

import java.util.Base64

internal data class EncryptedTokenPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal object KeystorePayloadCodec {
    private const val VERSION = "v1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(iv: ByteArray, ciphertext: ByteArray): String {
        require(iv.isNotEmpty()) { "IV must not be empty." }
        require(ciphertext.isNotEmpty()) { "Ciphertext must not be empty." }
        return listOf(VERSION, encoder.encodeToString(iv), encoder.encodeToString(ciphertext)).joinToString(".")
    }

    fun decode(value: String?): EncryptedTokenPayload? {
        if (value.isNullOrBlank()) return null
        val parts = value.split('.')
        if (parts.size != 3 || parts[0] != VERSION) return null
        return try {
            val iv = decoder.decode(parts[1])
            val ciphertext = decoder.decode(parts[2])
            if (iv.isEmpty() || ciphertext.isEmpty()) null else EncryptedTokenPayload(iv, ciphertext)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
