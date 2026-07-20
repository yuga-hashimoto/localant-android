package dev.localant.android.security

import java.security.SecureRandom

class TokenStore(private val backend: TokenBackend) {

    private val secureRandom = SecureRandom()
    private val encoder = java.util.Base64.getUrlEncoder().withoutPadding()

    fun current(): String? = backend.read()

    fun rotate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = encoder.encodeToString(bytes)
        backend.write(token)
        return token
    }
}
